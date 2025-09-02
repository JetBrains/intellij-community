package com.jetbrains.rhizomedb

import fleet.fastutil.ints.IntList

/**
 * Represents a primitive db query. Every complex query is composed of [IndexQuery].
 *
 * The variants of [IndexQuery] roughly resembles all possible [Datom] masks: [e? a? v?].
 * The only impossible mask is [* attr *].
 * */
sealed class IndexQuery<T> {
  /**
   * [e * *]
   * */
  data class Entity(val eid: EID) : IndexQuery<List<Datom>>()

  /**
   * [e a *] where `a` is [Cardinality.One]
   * */
  data class GetOne<T : Any>(
    val eid: EID,
    val attribute: Attribute<T>,
    val throwIfNoEntity: Boolean = false
  ) : IndexQuery<Versioned<T>?>()

  /**
   * [e a *] where `a` is [Cardinality.Many]
   * */
  data class GetMany<T : Any>(
    val eid: EID,
    val attribute: Attribute<T>
  ) : IndexQuery<List<Datom>>()

  /**
   * [* a v] where `a` is [Indexing.UNIQUE].
   * Uniqueness of [Attribute] is in some way symmetrical to it's [Cardinality]
   * */
  data class LookupUnique<T : Any>(
    val attribute: Attribute<T>,
    val value: T,
    val partitions: IntList = AllParts
  ) : IndexQuery<VersionedEID?>()

  /**
   * [* a v] where `a` is not [Indexing.UNIQUE]
   * */
  data class LookupMany<T : Any>(
    val attribute: Attribute<T>,
    val value: T,
    val partitions: IntList = AllParts
  ) : IndexQuery<List<Datom>>()

  /**
   * [* a *]
   * */
  data class Column<T : Any>(
    val attribute: Attribute<T>,
    val partitions: IntList = AllParts
  ) : IndexQuery<List<Datom>>()

  /**
   * [* * v] - supported only for refs.
   * */
  data class RefsTo(
    val eid: EID,
    val partitions: IntList = AllParts
  ) : IndexQuery<List<Datom>>()

  /**
   * [* * *]
   * Returns a lazy sequence of all [Datom]s in the [DB]
   * */
  data class All(val partitions: IntList = AllParts) : IndexQuery<Sequence<Datom>>()

  /**
   * [e a v]
   * */
  data class Contains<T : Any>(
    val eid: EID,
    val attribute: Attribute<T>,
    val value: T
  ) : IndexQuery<TX?>()
}

data class Versioned<T>(val x: T, val tx: TX)

data class VersionedEID(val eid: EID, val tx: TX)
