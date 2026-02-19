package com.jetbrains.rhizomedb

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
