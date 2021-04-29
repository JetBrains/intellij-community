// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir.inst

import com.intellij.codeInspection.dataFlow.DataFlowRunner
import com.intellij.codeInspection.dataFlow.DfaInstructionState
import com.intellij.codeInspection.dataFlow.DfaMemoryState
import com.intellij.codeInspection.dataFlow.InstructionVisitor
import com.intellij.codeInspection.dataFlow.jvm.JvmTrap
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
      transfer.traps.forEach { trap -> (trap as? JvmTrap)?.link(this) }
    }
  }

  override fun bindToFactory(factory: DfaValueFactory): Instruction {
    val instruction = ControlTransferInstruction(transfer.bindToFactory(factory), false)
    instruction.index = index
    return instruction
  }

  override fun accept(runner: DataFlowRunner, state: DfaMemoryState, visitor: InstructionVisitor): Array<out DfaInstructionState> {
    return visitor.visitControlTransfer(this, runner, state)
  }

  /**
   * Returns list of possible target instruction indices
   */
  fun getPossibleTargetIndices(): List<Int> = transfer.possibleTargetIndices

  override fun toString(): String = "TRANSFER $transfer [targets: ${getPossibleTargetIndices()}]"
}