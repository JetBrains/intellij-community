// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import com.jetbrains.rhizomedb.Attribute
import com.jetbrains.rhizomedb.DbContext
import com.jetbrains.rhizomedb.Instruction
import com.jetbrains.rhizomedb.Q
import fleet.util.serialization.ISerialization
import fleet.util.UID
import fleet.util.associateByUnique
import kotlin.reflect.KClass

fun interface InstructionEncoder {
  fun DbContext<Q>.encode(serContext: InstructionEncodingContext, instruction: Instruction): SharedInstructionData?
}

fun interface InstructionDecoder {
  fun DbContext<Q>.decode(deserContext: InstructionDecodingContext, sharedInstruction: SharedInstruction): List<Instruction>
}

data class SharedInstructionData(val sharedInstruction: SharedInstruction?)

data class InstructionEncodingContext(
  val json: ISerialization,
  val encoder: InstructionEncoder,
  val uidAttribute: Attribute<UID>
)

data class InstructionDecodingContext(
  val serialization: ISerialization,
  val decoder: InstructionDecoder,
  val uidAttribute: Attribute<UID>
)

interface InstructionCoder<I : Instruction, SI : SharedInstruction> {
  val instructionClass: KClass<I>
  val sharedInstructionClass: KClass<SI>?

  fun DbContext<Q>.encode(serContext: InstructionEncodingContext, instruction: I): SharedInstructionData?

  fun DbContext<Q>.decode(deserContext: InstructionDecodingContext, sharedInstruction: SI): List<Instruction>
}

interface UniversalInstruction : SharedInstruction, Instruction {
  abstract class IdentityCoder<T : UniversalInstruction>(c: KClass<T>) : InstructionCoder<T, T> {
    override val instructionClass: KClass<T> = c
    override val sharedInstructionClass: KClass<T> = c

    override fun DbContext<Q>.encode(serContext: InstructionEncodingContext, instruction: T): SharedInstructionData =
      SharedInstructionData(instruction)

    override fun DbContext<Q>.decode(deserContext: InstructionDecodingContext, sharedInstruction: T): List<T> =
      listOf(sharedInstruction)
  }
}

data class InstructionSet(val coders: List<InstructionCoder<*, *>>) {
  operator fun plus(coder: InstructionCoder<*, *>): InstructionSet =
    copy(coders + coder)
}

fun InstructionSet.encoder(): InstructionEncoder {
  val coders = coders.associateByUnique { coder -> coder.instructionClass }
  return InstructionEncoder { serContext, i ->
    coders[i::class]?.let { coder ->
      with(coder as InstructionCoder<Instruction, SharedInstruction>) {
        encode(serContext, i)
      }
    }
  }
}

fun InstructionSet.decoder(): InstructionDecoder {
  val coders = coders
    .filter { coder -> coder.sharedInstructionClass != null }
    .associateByUnique { coder -> coder.sharedInstructionClass }
  return InstructionDecoder { deserContext, i ->
    val coder = requireNotNull(coders[i::class]) {
      "no InstructionCoder found for instruction ${i::class}"
    } as InstructionCoder<Instruction, SharedInstruction>
    with(coder) {
      decode(deserContext, i)
    }
  }
}
