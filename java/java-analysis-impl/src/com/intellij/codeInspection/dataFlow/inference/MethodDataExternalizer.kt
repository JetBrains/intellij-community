// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference

import com.intellij.codeInsight.Nullability
import com.intellij.codeInspection.dataFlow.ContractReturnValue
import com.intellij.codeInspection.dataFlow.StandardMethodContract
import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil.*
import java.io.DataInput
import java.io.DataOutput
import java.util.*

/**
 * @author peter
 */
internal object MethodDataExternalizer : DataExternalizer<Map<Int, MethodData>> {

  override fun save(out: DataOutput, value: Map<Int, MethodData>?) {
    writeSeq(out, value!!.toList()) { writeINT(out, it.first); writeMethod(
      out, it.second)
    }
  }

  override fun read(input: DataInput) = readSeq(input) { readINT(input) to readMethod(
    input)
  }.toMap()

  private fun writeMethod(out: DataOutput, data: MethodData) {
    writeNullable(out, data.methodReturn) { writeNullity(out, it) }
    writeNullable(out, data.purity) { writePurity(out, it) }
    writeSeq(out, data.contracts) { writeContract(out, it) }
    writeBitSet(out, data.notNullParameters)
    writeINT(out, data.bodyStart)
    writeINT(out, data.bodyEnd)
  }

  private fun readMethod(input: DataInput): MethodData {
    val nullity = readNullable(input) { readNullity(input) }
    val purity = readNullable(input) { readPurity(input) }
    val contracts = readSeq(input) { readContract(input) }
    val notNullParameters = readBitSet(input)
    return MethodData(nullity, purity, contracts, notNullParameters, readINT(input),
                                                                     readINT(input))
  }

  private fun writeBitSet(out: DataOutput, bitSet: BitSet) {
    val bytes = bitSet.toByteArray()
    val size = bytes.size
    // Write up to 255 bytes, thus up to 2040 bits which is far more than number of allowed Java method parameters
    assert(size in 0..255)
    out.writeByte(size)
    out.write(bytes)
  }

  private fun readBitSet(input: DataInput): BitSet {
    val size = input.readUnsignedByte()
    val bytes = ByteArray(size)
    input.readFully(bytes)
    return BitSet.valueOf(bytes)
  }

  private fun writeNullity(out: DataOutput, methodReturn: MethodReturnInferenceResult) = when (methodReturn) {
    is MethodReturnInferenceResult.Predefined -> { out.writeByte(0); out.writeByte(methodReturn.value.ordinal) }
    is MethodReturnInferenceResult.FromDelegate -> {
      out.writeByte(1); out.writeByte(methodReturn.value.ordinal); writeRanges(
        out, methodReturn.delegateCalls)
    }
    else -> throw IllegalArgumentException(methodReturn.toString())
  }
  private fun readNullity(input: DataInput): MethodReturnInferenceResult = when (input.readByte().toInt()) {
    0 -> MethodReturnInferenceResult.Predefined(
      Nullability.values()[input.readByte().toInt()])
    else -> MethodReturnInferenceResult.FromDelegate(
      Nullability.values()[input.readByte().toInt()],
      readRanges(input))
  }

  private fun writeRanges(out: DataOutput, ranges: List<ExpressionRange>) = writeSeq(out, ranges) {
    writeRange(out, it)
  }
  private fun readRanges(input: DataInput) = readSeq(input) {
    readRange(input)
  }

  private fun writeRange(out: DataOutput, range: ExpressionRange) {
    writeINT(out, range.startOffset)
    writeINT(out, range.endOffset)
  }
  private fun readRange(input: DataInput) = ExpressionRange(readINT(input), readINT(input))

  private fun writePurity(out: DataOutput, purity: PurityInferenceResult) {
    writeRanges(out, purity.mutatedRefs)
    writeNullable(out, purity.singleCall) { writeRange(out, it) }
  }
  private fun readPurity(input: DataInput) = PurityInferenceResult(
    readRanges(input),
    readNullable(input) { readRange(input) })

  private fun writeContract(out: DataOutput, contract: PreContract): Unit = when (contract) {
    is DelegationContract -> { out.writeByte(0); writeRange(
      out, contract.expression); out.writeBoolean(contract.negated) }
    is KnownContract -> { out.writeByte(1)
      writeContractArguments(out, contract.contract.constraints)
      out.writeByte(contract.contract.returnValue.ordinal())
    }
    is MethodCallContract -> { out.writeByte(2)
      writeRange(out, contract.call)
      writeSeq(out, contract.states) {
        writeContractArguments(out, it)
      }
    }
    is NegatingContract -> { out.writeByte(3); writeContract(
      out, contract.negated)
    }
    is SideEffectFilter -> { out.writeByte(4)
      writeRanges(out, contract.expressionsToCheck)
      writeSeq(out, contract.contracts) { writeContract(out, it) }
    }
    else -> throw IllegalArgumentException(contract.toString())
  }
  private fun readContract(input: DataInput): PreContract = when (input.readByte().toInt()) {
    0 -> DelegationContract(
      readRange(input), input.readBoolean())
    1 -> KnownContract(StandardMethodContract(
      readContractArguments(input).toTypedArray(),
      readReturnValue(input)))
    2 -> MethodCallContract(
      readRange(input),
      readSeq(input) { readContractArguments(input) })
    3 -> NegatingContract(
      readContract(input))
    else -> SideEffectFilter(
      readRanges(input),
      readSeq(input) { readContract(input) })
  }

  private fun writeContractArguments(out: DataOutput, arguments: List<ValueConstraint>) =
      writeSeq(out, arguments) { out.writeByte(it.ordinal) }
  private fun readContractArguments(input: DataInput) = readSeq(input, {
    readValueConstraint(input)
  })

  private fun readValueConstraint(input: DataInput) = ValueConstraint.values()[input.readByte().toInt()]

  private fun readReturnValue(input: DataInput) = ContractReturnValue.valueOf(input.readByte().toInt())

}
