package com.jetbrains.rhizomedb

/**
 * Data model behind rhizomedb is a set of triples [entity attribute value]. See Datomic and RDF.
 *
 * [Datom] represents an element of the transaction novelty. Any change to the db may be encoded as a set of [Datom]s.
 * It may be matched them against [IndexQuery] using [Pattern]s.
 *
 * @see EID
 * @see Attribute
 * @see TX
 * */
data class Datom(val eid: EID, val attr: Attribute<*>, val value: Any, val tx: TX, val added: Boolean = true) {
  override fun toString(): String {
    return "Datom[$eid, $attr, $value, $tx, $added]"
  }
}

data class EAV(val eid: EID, val attr: Attribute<*>, val value: Any)

val Datom.eav: EAV get() = EAV(eid, attr, value)

data class EAVa(val eid: EID, val attr: Attribute<*>, val value: Any, val added: Boolean)

val EAVa.eav: EAV get() = EAV(eid, attr, value)
