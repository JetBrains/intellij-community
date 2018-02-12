/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.intellij.codeInspection.dataFlow

import com.intellij.codeInspection.dataFlow.instructions.Instruction
import com.intellij.codeInspection.dataFlow.value.DfaPsiType
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.psi.*
import com.intellij.util.containers.FList
import java.util.*

/**
 * @author peter
 */
class DfaControlTransferValue(factory: DfaValueFactory,
                              val target: TransferTarget,
                              val traps: FList<Trap>) : DfaValue(factory) {
  override fun toString() = target.toString() + " " + traps.toString()
}

interface TransferTarget
data class ExceptionTransfer(val throwable: DfaPsiType?) : TransferTarget
data class InstructionTransfer(val offset: ControlFlow.ControlFlowOffset, val toFlush: List<DfaVariableValue>) : TransferTarget
object ReturnTransfer : TransferTarget {
  override fun toString(): String = "ReturnTransfer"
}

open class ControlTransferInstruction(val transfer: DfaControlTransferValue?) : Instruction() {
  override fun accept(runner: DataFlowRunner, state: DfaMemoryState, visitor: InstructionVisitor): Array<out DfaInstructionState> {
    return visitor.visitControlTransfer(this, runner, state)
  }

  fun getPossibleTargetIndices() : List<Int> {
    if (transfer == null) return emptyList()

    val result = ArrayList(transfer.traps.flatMap(Trap::getPossibleTargets))
    if (transfer.target is InstructionTransfer) {
      result.add(transfer.target.offset.instructionOffset)
    }
    return result
  }

  fun getPossibleTargetInstructions(allInstructions: Array<Instruction>) = getPossibleTargetIndices().map { allInstructions[it] }

  override fun toString() = if (transfer == null) "RET" else "TRANSFER " + transfer.toString()
}

sealed class Trap(val anchor: PsiElement) {
  class TryCatch(tryStatement: PsiTryStatement, val clauses: LinkedHashMap<PsiCatchSection, ControlFlow.ControlFlowOffset>)
    : Trap(tryStatement) {
    override fun toString(): String = "TryCatch -> " + clauses.values
  }
  class TryFinally(finallyBlock: PsiCodeBlock, val jumpOffset: ControlFlow.ControlFlowOffset): Trap(finallyBlock) {
    override fun toString(): String = "TryFinally -> " + jumpOffset
  }
  class TwrFinally(resourceList: PsiResourceList, val jumpOffset: ControlFlow.ControlFlowOffset): Trap(resourceList) {
    override fun toString(): String = "TwrFinally -> " + jumpOffset
  }
  class InsideFinally(finallyBlock: PsiElement): Trap(finallyBlock) {
    override fun toString(): String = "InsideFinally"
  }

  internal fun getPossibleTargets(): Collection<Int> {
    return when (this) {
      is TryCatch -> clauses.values.map { it.instructionOffset }
      is TryFinally -> listOf(jumpOffset.instructionOffset)
      is TwrFinally -> listOf(jumpOffset.instructionOffset)
      else -> emptyList()
    }
  }
}

private class ControlTransferHandler(val state: DfaMemoryState, val runner: DataFlowRunner, val target: TransferTarget) {
  var throwableState: DfaVariableState? = null

  fun iteration(traps: FList<Trap>): List<DfaInstructionState> {
    val (head, tail) = traps.head to traps.tail
    return when (head) {
      null -> transferToTarget()
      is Trap.TryCatch -> if (target is ExceptionTransfer) processCatches(head, target.throwable, tail) else iteration(tail)
      is Trap.TryFinally -> goToFinally(head.jumpOffset.instructionOffset, tail)
      is Trap.TwrFinally -> if (target is ExceptionTransfer) iteration(tail) else goToFinally(head.jumpOffset.instructionOffset, tail)
      is Trap.InsideFinally -> leaveFinally(tail)
    }
  }

  private fun transferToTarget(): List<DfaInstructionState> {
    return when (target) {
      is InstructionTransfer -> {
        target.toFlush.forEach { state.flushVariable(it) }
        listOf(DfaInstructionState(runner.getInstruction(target.offset.instructionOffset), state))
      }
      else -> emptyList()
    }
  }

  private fun goToFinally(offset: Int, traps: FList<Trap>): List<DfaInstructionState> {
    state.push(runner.factory.controlTransfer(target, traps))
    return listOf(DfaInstructionState(runner.getInstruction(offset), state))
  }

  private fun leaveFinally(traps: FList<Trap>): List<DfaInstructionState> {
    state.pop() as DfaControlTransferValue
    return iteration(traps)
  }

  private fun processCatches(tryCatch: Trap.TryCatch, thrownValue: DfaPsiType?, traps: FList<Trap>): List<DfaInstructionState> {
    val result = arrayListOf<DfaInstructionState>()
    for ((catchSection, jumpOffset) in tryCatch.clauses) {
      val param = catchSection.parameter ?: continue
      if (throwableState == null) throwableState = initVariableState(param, thrownValue)

      for (caughtType in allCaughtTypes(param)) {
        throwableState?.withInstanceofValue(caughtType)?.let { varState ->
          result.add(DfaInstructionState(runner.getInstruction(jumpOffset.instructionOffset), stateForCatchClause(param, varState)))
        }

        throwableState = throwableState?.withNotInstanceofValue(caughtType) ?: return result
      }
    }
    return result + iteration(traps)
  }

  private fun allCaughtTypes(param: PsiParameter): List<DfaPsiType> {
    val psiTypes = param.type.let { if (it is PsiDisjunctionType) it.disjunctions else listOfNotNull(it) }
    return psiTypes.map { runner.factory.createDfaType(it) }
  }

  private fun stateForCatchClause(param: PsiParameter, varState: DfaVariableState): DfaMemoryState {
    val catchingCopy = state.createCopy() as DfaMemoryStateImpl
    catchingCopy.setVariableState(catchingCopy.factory.varFactory.createVariableValue(param, false), varState)
    return catchingCopy
  }

  private fun initVariableState(param: PsiParameter, throwable: DfaPsiType?): DfaVariableState {
    val sampleVar = (state as DfaMemoryStateImpl).factory.varFactory.createVariableValue(param, false)
    val varState = state.createVariableState(sampleVar).withFact(DfaFactType.CAN_BE_NULL, false)
    return if (throwable != null) varState.withInstanceofValue(throwable)!! else varState
  }

}
