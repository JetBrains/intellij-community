// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import com.jetbrains.rhizomedb.impl.EidGen
import com.jetbrains.rhizomedb.impl.generateSeed
import fleet.util.openmap.Key
import fleet.util.openmap.MutableOpenMap
import it.unimi.dsi.fastutil.ints.IntList

/**
 * Main interface for queries.
 *
 * It exists to abstract DB and MutableDb and allow for various interceptions and customizations.
 * */
interface Q {
  /**
   * Indexes access. Allows to implement and intercept all of the data access.
   * i.e. used to implement read-tracking
   * @see IndexQuery
   * */
  fun <T> queryIndex(indexQuery: IndexQuery<T>): T

  /**
   * Performs cached query. [CachedQuery] is read-tracked and memoized based of the trace.
   * Part of the query result is a read-trace of the query.
   * On each transaction, stale values are persistently evicted from cache.
   * Previous cache is untouched. The cache acts like a value.
   * Other eviction strategies are implementation details.
   * */
  fun <T> DbContext<Q>.cachedQuery(query: CachedQuery<T>): CachedQueryResult<T>

  /**
   * Separate way to assert entity existence. It exists as a separate method from [queryIndex] because it should not be tracked as a regular read,
   * because it may yield exactly one result.
   * */
  fun assertEntityExists(eid: EID, accessedAttribute: Attribute<*>?, referenceAttribute: Attribute<*>?): Nothing?

  /**
   * A way to reset the pipeline.
   * [original] is guaranteed to be either [DB] or [MutableDb]
   * */
  val original: Q
}

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

/**
 * Represents attribute in [Datom].
 *
 * [Attribute] itsef is an Entity.
 * [EID] behind it also contains some minimal information about it's [Schema]
 * */
@JvmInline
value class Attribute<T : Any>(private val attr: EID) {
  companion object {
    /**
     * Combines [EID] with [Schema] to build an [Attribute]
     * */
    fun <T : Any> fromEID(eid: EID, schema: Schema): Attribute<T> = Attribute(schema.value.shl(20).or(eid))
  }

  val eid: EID get() = attr
  val schema: Schema get() = Schema(attr.shr(20))
}

/**
 * Represents cardinality of a particular [Attribute].
 * Every [Attribute] is either multi-valued or no-more-than-single-valued.
 * */
enum class Cardinality {
  One, Many
}

/**
 * Represents [Entity] id.
 * */
typealias EID = Int
/**
 * Fourth element of the [Datom].
 * It is an opaque [Long] value, representing some information about the transaction, when this [Datom] was added to the [DB].
 * */
typealias TX = Long
/**
 * Every [EID] has a partition, encoded in it.
 * Different partitions may be separated and combined efficiently to form different [DB]s.
 * There is one dedicated partition for schema, it's value is 0
 * */
typealias Part = Int

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

/**
 * Result of [Q.cachedQuery]
 * */
class CachedQueryResult<T>(val result: T, val patterns: LongArray)

/**
 * Implementation serves as a cache key, should be equal when arguments of the query are equal
 */
interface CachedQuery<out T> {
  fun DbContext<Q>.query(): T
}

/**
 * Interface for db mutation.
 * It's prime implementation is [MutableDb].
 * Allows for various interceptions and customization.
 * */
interface Mut : Q {
  /**
   * Partition where all new entities are created by this instance.
   * */
  val defaultPart: Part

  /**
   * Persistent snapshot of [DB] from which the transaction is started.
   * */
  val dbBefore: DB

  /**
   * Original [MutableDb] instance, same as [DB.original].
   * It allows to reset the pipeline and to build a new one with the same backing storage.
   * */
  val mutableDb: MutableDb

  /**
   * Mutable user-data map, associated with this [MutableDb].
   * It's the same map, returned by [DB.change] in [Change.meta]
   * */
  val meta: MutableOpenMap<ChangeScope>

  /**
   * Expands [Instruction] into more primitive [Op.Assert]s and [Op.Retract]s, given a [Q] interface.
   * It is not allowed to perform any mutations.
   * */
  fun expand(pipeline: DbContext<Q>, instruction: Instruction): Expansion = run {
    val expansionResult = with(instruction) { pipeline.expand() }
    Expansion(ops = expansionResult.ops,
              instruction = instruction,
              tx = instruction.seed,
              effects = expansionResult.effects,
              sharedInstruction = null)
  }

  /**
   * Responsible for [EID] generation for the new entities and [initials] interception.
   * See the default implementation below.
   * */
  fun createEntity(pipeline: DbContext<Mut>, entityTypeEid: EID, initials: List<Pair<Attribute<*>, Any>>): EID =
    EidGen.freshEID(pipeline.impl.defaultPart).also { eid ->
      pipeline.mutate(CreateEntity(eid = eid,
                                   entityTypeEid = entityTypeEid,
                                   attributes = initials,
                                   seed = generateSeed()))
    }

  /**
   * The only function which actually performs mutation.
   * It is required that effects of mutation is visible immediately after [mutate] returns.
   * It is required to return a precise [Novelty] produced by this [Expansion].
   * */
  fun mutate(pipeline: DbContext<Mut>, expansion: Expansion): Novelty
}

/**
 * Bytecode of transaction.
 * It represents a primitive operation, which is atomically performed on db.
 * It is atomic in a sense that intermediate DB state is not only hidden from other threads, but also is not representable at all.
 *
 * [Instruction] performs reads from the db and returns a set of primitive [Op.Assert]s and [Op.Retract]s.
 * It is not allow to perform mutations. Mutation itself is atomically performed by [Mut.mutate].
 * */
interface Instruction {
  data class Const(override val seed: Long,
                   val effects: List<InstructionEffect>,
                   val result: List<Op>) : Instruction {
    override fun DbContext<Q>.expand(): InstructionExpansion =
      InstructionExpansion(result, effects)
  }

  /**
   * Usually it is a random number, which takes part in [Datom.tx] computation.
   * */
  val seed: Long
  /**
   * Performs the actual expansion. It MUST be a totally side-effect-free, hermetic, pure function.
   * It is not allowed to read any other state than [Q] which it is given.
   * */
  fun DbContext<Q>.expand(): InstructionExpansion
}

/**
 * The result of [Instruction] expansion.
 * */
data class Expansion(val ops: List<Op>,
                     val tx: TX,
                     val instruction: Instruction,
                     val sharedInstruction: Any?,
                     val effects: List<InstructionEffect>)

/**
 * Key for data associated with a particular change
 */
interface ChangeScopeKey<V : Any> : Key<V, ChangeScope>

/**
 * Primitive operation, to which [Instruction] is expanded to.
 * */
sealed class Op {
  data class Assert(val eid: EID, val attribute: Attribute<*>, val value: Any) : Op()
  data class Retract(val eid: EID, val attribute: Attribute<*>, val value: Any) : Op()

  /*
  * this is HACK!!!
  * Exchange Op preserves the tx of the existing Datom
  * The instruction seed will be ignored
  * Can be used for changing value representation only, but not the semantics
  *
  * It is used for deserialization of values already present in the DB when we load code
  * */
  data class AssertWithTX(val eid: EID, val attribute: Attribute<*>, val value: Any, val tx: TX) : Op()
}

/**
 * Instruction may not only expand into primitive [Op]s but may also yield an effect.
 * How and when the effect is executed is a subject for polymorphism and particular [Mut] implementation.
 * Generally it may be thought of as an asynchronous instruction continuation,
 * which has to be performed when/if the instruction was applied successfully.
 * */
data class InstructionEffect(val origin: Instruction,
                             val effect: DbContext<Mut>.() -> Unit)

/**
 * Result of [Instruction.expand]
 * */
data class InstructionExpansion(val ops: List<Op>,
                                val effects: List<InstructionEffect> = emptyList())

/**
 * Exception type, which is raised by [Mut] implementation if something goes wrong.
 * */
class TxValidationException(message: String) : RuntimeException(message)

/**
 * [DB] may have no more than 5 partitions, including schema.
 * */
internal const val MAX_PART = 4

/** [0 1 2 3 4] */
val AllParts: IntList = IntList.of(*(0..MAX_PART).toList().toIntArray())

const val SchemaPart: Part = 0 // schema

/**
 * Extrancts the partition part from the [EID]
 * */
fun partition(e: EID): Part = e.ushr(28)

/**
 * Sets the partition part of the [EID]
 * */
fun withPart(e: EID, part: Part): EID = e.or(part.shl(28))
