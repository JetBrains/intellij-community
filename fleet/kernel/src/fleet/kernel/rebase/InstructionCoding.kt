// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import com.jetbrains.rhizomedb.Attribute
import com.jetbrains.rhizomedb.DbContext
import com.jetbrains.rhizomedb.Instruction
import com.jetbrains.rhizomedb.Q
import fleet.util.serialization.ISerialization
import fleet.util.UID
import fleet.util.associateByUnique
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.KSerializer
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
  val uidAttribute: Attribute<UID>,
)

data class InstructionDecodingContext(
  val serialization: ISerialization,
  val decoder: InstructionDecoder,
  val uidAttribute: Attribute<UID>,
)

interface InstructionCoder<I : Instruction, SI> {
  val instructionClass: KClass<I>
  val serializer: KSerializer<SI>
  val instructionName: String?

  fun DbContext<Q>.encode(serContext: InstructionEncodingContext, instruction: I): SharedInstructionData?

  fun DbContext<Q>.decode(deserContext: InstructionDecodingContext, sharedInstruction: SI): List<Instruction>
}

fun<SI> InstructionCoder<*, SI>.sharedInstruction(si: SI): SharedInstruction = 
  SharedInstruction(instructionName!!, SerializedValue.fromDeserializedValue(si, serializer))

interface UniversalInstruction : Instruction {
  abstract class IdentityCoder<T : UniversalInstruction>(
    override val instructionClass: KClass<T>,
    override val serializer: KSerializer<T>,
    override val instructionName: String,
  ) : InstructionCoder<T, T> {
    override fun DbContext<Q>.encode(serContext: InstructionEncodingContext, instruction: T): SharedInstructionData =
      SharedInstructionData(SharedInstruction(instructionName, SerializedValue.fromDeserializedValue(instruction, serializer)))

    override fun DbContext<Q>.decode(deserContext: InstructionDecodingContext, sharedInstruction: T): List<T> =
      listOf(sharedInstruction)
  }
}

data class InstructionSet(private val coders: List<InstructionCoder<*, *>>) {
  private val byName = coders
    .filter { coder -> coder.instructionName != null }
    .associateByUnique { coder -> coder.instructionName }

  private val byInstruction = coders
    .associateByUnique { coder -> coder.instructionClass }

  operator fun plus(coder: InstructionCoder<*, *>): InstructionSet =
    copy(coders = coders + coder)
  
  fun encoder(): InstructionEncoder = InstructionEncoder { serContext, instruction ->
    byInstruction[instruction::class]?.let { coder ->
      with(coder as InstructionCoder<Instruction, *>) {
        encode(serContext, instruction)
      }
    }
  }

  fun decoder(): InstructionDecoder = InstructionDecoder { deserContext, sharedInstruction ->
    val coder = requireNotNull(byName[sharedInstruction.name]) {
      "no InstructionCoder found for instruction ${sharedInstruction.name}"
    } as InstructionCoder<*, Any>
    with(coder) {
      decode(deserContext, sharedInstruction.instruction.get(coder.serializer))
    }
  }
}
