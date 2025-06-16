package com.jetbrains.rhizomedb

import com.jetbrains.rhizomedb.impl.EidGen
import com.jetbrains.rhizomedb.impl.generateSeed
import fleet.util.openmap.MutableOpenMap

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