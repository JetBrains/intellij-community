// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.jvm.transfer.EnterFinallyTrap
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory

/**
 * Instruction which performs complex control transfer (handling exception; processing finally blocks; exiting inlined lambda, etc.)
 */
open class ControlTransferInstruction : Instruction {
  val transfer: DfaControlTransferValue

  constructor(transfer: DfaControlTransferValue) : this(transfer, true)

  constructor(transfer: DfaControlTransferValue, linkTraps: Boolean) : super() {
    this.transfer = transfer
    if (linkTraps) {
      transfer.traps.forEach { trap -> (trap as? EnterFinallyTrap)?.link(this) }
    }
  }

  override fun bindToFactory(factory: DfaValueFactory): Instruction {
    val instruction = ControlTransferInstruction(transfer.bindToFactory(factory), false)
    instruction.index = index
    return instruction
  }

  override fun accept(interpreter: DataFlowInterpreter, state: DfaMemoryState): Array<out DfaInstructionState> {
    return this.transfer.dispatch(state, interpreter).toTypedArray()
  }

  /**
   * Returns list of possible target instruction indices
   */
  override fun getSuccessorIndexes(): IntArray = transfer.possibleTargetIndices

  override fun toString(): String {
    val successorIndexes = successorIndexes
    return "TRANSFER $transfer" + if (successorIndexes.isEmpty()) "" else " [targets: ${successorIndexes.contentToString()}]"
  }
}