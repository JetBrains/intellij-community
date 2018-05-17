// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions

import com.intellij.codeInspection.dataFlow.*

/**
 * Instruction which performs complex control transfer (handling exception; processing finally blocks; exiting inlined lambda, etc.)
 */
open class ControlTransferInstruction(val transfer: DfaControlTransferValue) : Instruction() {
  init {
    transfer.traps.forEach { trap -> trap.link(this) }
  }

  override fun accept(runner: DataFlowRunner, state: DfaMemoryState, visitor: InstructionVisitor): Array<out DfaInstructionState> {
    return visitor.visitControlTransfer(this, runner, state)
  }

  /**
   * Returns list of possible target instruction indices
   */
  fun getPossibleTargetIndices() = transfer.traps.flatMap(Trap::getPossibleTargets) + transfer.target.getPossibleTargets()

  override fun toString() = "TRANSFER $transfer [targets: ${getPossibleTargetIndices()}]"
}