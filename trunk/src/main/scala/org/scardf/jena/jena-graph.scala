package org.scardf.jena

import scala.collection.mutable.ArrayBuffer
import com.hp.hpl.jena.rdf.model.{Literal => JLiteral, _}
import com.hp.hpl.jena.datatypes.TypeMapper

class JenaGraph( private val m: Model ) extends Graph {
  
  def this() = this( ModelFactory.createDefaultModel )

  def triples = new JenaTripleIterator( m.listStatements ).toList

  def contains( t: Triple ) = m contains statement( t )

  override def +( t: Triple ) = {
    m add statement( t )
    this
  }
  
  override def =~( that: Graph ): Boolean = that match {
    case jg: JenaGraph => m isIsomorphicWith jg.m
    case g => super.=~( g )
  }
  
  override def subjects: Set[SubjectNode] = Set() ++ new JenaResIterator( m.listSubjects )

  override def queryEngineOpt = Some( new JenaArq( m ) )
  
  def statement( t: Triple ): Statement = 
    m.createStatement( resource( t.sub ), property( t.pred ), rdfnode( t.obj ) )
  
  def resource( sn: SubjectNode ) = sn match {
    case b: Blank => m createResource new AnonId( b.id )
    case u: UriRef => property( u )
  }
  
  def property( p: UriRef ) = m.createProperty( p.uri )
  
  def rdfnode( n: Node ): RDFNode = n match {
    case sn: SubjectNode => resource( sn )
    case PlainLiteral( lf, None ) => ResourceFactory.createPlainLiteral( lf )
    case PlainLiteral( lf, Some( langtag ) ) => m.createLiteral( lf, langtag.code )
    case TypedLiteral( lf, UriRef( dtUri ) ) => 
      ResourceFactory.createTypedLiteral( lf, TypeMapper.getInstance.getSafeTypeByName( dtUri ) )
  }

  override def ++( ts: Iterable[Triple] ) = { ts foreach { this+_ }; this }
  
  override def renderIn( sf: SerializationFormat ): Serializator = new Serializator() {
    override def writeTo( w: java.io.Writer ) = {
      bindings foreach { p => m.setNsPrefix( p._1, p._2 ) }
      m.write( w, Jena codeFor sf )
    }
    override def readFrom( r: java.io.Reader ) = {
      m.read( r, null, Jena codeFor sf )
      JenaGraph.this
    }
  }
}

class JenaResIterator( jIterator: ResIterator ) extends Iterator[SubjectNode] {
  override def hasNext = jIterator.hasNext
  override def next = Jena.subjectNode( jIterator.next.asInstanceOf[Resource] )
}

class JenaTripleIterator( jIterator: StmtIterator ) extends Iterator[Triple] {
  override def hasNext = jIterator.hasNext
  override def next = Jena.triple( jIterator.next.asInstanceOf[Statement] )
}

object Jena {
  def subjectNode( r: Resource ): SubjectNode = 
    if ( r.isAnon ) Blank( r.getId.getLabelString ) else uriRef( r )
  
  def uriRef( r: Resource ) = UriRef( r.getURI )
  
  def node( n: RDFNode ): Node = n match {
    case null        => null
    case p: Property => uriRef( p )
    case r: Resource => subjectNode( r )
    case l: JLiteral => literal( l )
  }
  
  def literal( l: JLiteral ) = {
    val lexForm = l.getLexicalForm
    val typeUri = l.getDatatypeURI
    if ( typeUri == null ) PlainLiteral( lexForm, LangTag.opt( l.getLanguage ) )
    else TypedLiteral( lexForm, UriRef( typeUri ) )
  }
  
  def triple( s: Statement ) = Triple( 
    subjectNode( s.getSubject ), 
    uriRef( s.getPredicate ), 
    node( s.getObject ) 
  )
  
  def codeFor( sf: SerializationFormat ) = sf match {
    case RdfXml => "RDF/XML"
    case NTriple => "N-TRIPLE"
    case Turtle => "TURTLE"
    case N3 => "N3"
    case _ => null
  }
}