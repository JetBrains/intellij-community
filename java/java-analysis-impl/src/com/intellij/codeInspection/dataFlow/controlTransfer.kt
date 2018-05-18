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

import com.intellij.codeInspection.dataFlow.instructions.ControlTransferInstruction
import com.intellij.codeInspection.dataFlow.value.DfaPsiType
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.psi.*
import com.intellij.util.containers.FList
import java.util.*
import kotlin.collections.ArrayList

/**
 * @author peter
 */
class DfaControlTransferValue(factory: DfaValueFactory,
                              val target: TransferTarget,
                              val traps: FList<Trap>) : DfaValue(factory) {
  fun dispatch(state: DfaMemoryState, runner: DataFlowRunner) = ControlTransferHandler(state, runner, this).dispatch()
  override fun toString() = target.toString() + (if (traps.isEmpty()) "" else " $traps")
}

interface TransferTarget {
  /** @return list of possible instruction offsets for given target */
  fun getPossibleTargets() : Collection<Int> = emptyList()
  /** @return next instruction states assuming no traps */
  fun dispatch(state: DfaMemoryState, runner: DataFlowRunner) : List<DfaInstructionState> = emptyList()
}
data class ExceptionTransfer(val throwable: DfaPsiType?) : TransferTarget {
  override fun toString() = "Exception($throwable)"
}
data class InstructionTransfer(val offset: ControlFlow.ControlFlowOffset, private val toFlush: List<DfaVariableValue>) : TransferTarget {
  override fun dispatch(state: DfaMemoryState, runner: DataFlowRunner): List<DfaInstructionState> {
    toFlush.forEach(state::flushVariable)
    return listOf(DfaInstructionState(runner.getInstruction(offset.instructionOffset), state))
  }

  override fun getPossibleTargets() = listOf(offset.instructionOffset)
  override fun toString() = "-> $offset" + (if (toFlush.isEmpty()) "" else "; flushing $toFlush")
}
data class ExitFinallyTransfer(private val enterFinally: Trap.EnterFinally) : TransferTarget {
  override fun getPossibleTargets() = enterFinally.backLinks.asIterable().flatMap { it.getPossibleTargetIndices() }
    .filter { index -> index != enterFinally.jumpOffset.instructionOffset }.toSet()

  override fun dispatch(state: DfaMemoryState, runner: DataFlowRunner): List<DfaInstructionState> {
    return (state.pop() as DfaControlTransferValue).dispatch(state, runner)
  }

  override fun toString() = "ExitFinally"
}
object ReturnTransfer : TransferTarget {
  override fun toString(): String = "Return"
}

sealed class Trap(val anchor: PsiElement) {
  open fun link(instruction: ControlTransferInstruction) {}

  internal abstract fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState>
  internal open fun getPossibleTargets(): Collection<Int> = emptyList()
  override fun toString() = javaClass.simpleName!!

  class TryCatch(tryStatement: PsiTryStatement, val clauses: LinkedHashMap<PsiCatchSection, ControlFlow.ControlFlowOffset>)
    : Trap(tryStatement) {
    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> {
      return if (handler.target is ExceptionTransfer) handler.processCatches(handler.target.throwable, clauses)
      else handler.dispatch()
    }

    override fun getPossibleTargets() = clauses.values.map { it.instructionOffset }
    override fun toString() = "${super.toString()} -> ${clauses.values}"
  }
  abstract class EnterFinally(anchor: PsiElement, val jumpOffset: ControlFlow.ControlFlowOffset): Trap(anchor) {
    internal val backLinks = ArrayList<ControlTransferInstruction>()

    override fun link(instruction: ControlTransferInstruction) {
      backLinks.add(instruction)
    }

    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> {
      handler.state.push(handler.runner.factory.controlTransfer(handler.target, handler.traps))
      return listOf(DfaInstructionState(handler.runner.getInstruction(jumpOffset.instructionOffset), handler.state))
    }

    override fun getPossibleTargets() = listOf(jumpOffset.instructionOffset)
    override fun toString() = "${super.toString()} -> $jumpOffset"
  }
  class TryFinally(finallyBlock: PsiCodeBlock, jumpOffset: ControlFlow.ControlFlowOffset): EnterFinally(finallyBlock, jumpOffset)
  class TwrFinally(resourceList: PsiResourceList, jumpOffset: ControlFlow.ControlFlowOffset) : EnterFinally(resourceList, jumpOffset) {
    override fun dispatch(handler: ControlTransferHandler) =
      if (handler.target is ExceptionTransfer) handler.dispatch()
      else super.dispatch(handler)
  }
  class InsideFinally(finallyBlock: PsiElement): Trap(finallyBlock) {
    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> {
      handler.state.pop() as DfaControlTransferValue
      return handler.dispatch()
    }
  }
  class InsideInlinedBlock(block: PsiCodeBlock): Trap(block) {
    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> {
      (handler.state.pop() as DfaControlTransferValue).target as ReturnTransfer
      return handler.dispatch()
    }
  }
}

internal class ControlTransferHandler(val state: DfaMemoryState, val runner: DataFlowRunner, transferValue: DfaControlTransferValue) {
  private var throwableType: TypeConstraint? = null
  val target = transferValue.target
  var traps = transferValue.traps

  fun dispatch(): List<DfaInstructionState> {
    val head = traps.head
    traps = traps.tail ?: FList.emptyList()
    state.emptyStack()
    return head?.dispatch(this) ?: target.dispatch(state, runner)
  }

  internal fun processCatches(thrownValue: DfaPsiType?,
                              catches: Map<PsiCatchSection, ControlFlow.ControlFlowOffset>): List<DfaInstructionState> {
    val result = arrayListOf<DfaInstructionState>()
    for ((catchSection, jumpOffset) in catches) {
      val param = catchSection.parameter ?: continue
      if (throwableType == null) {
        throwableType = thrownValue?.asConstraint() ?: TypeConstraint.EMPTY
      }

      for (caughtType in allCaughtTypes(param)) {
        throwableType?.withInstanceofValue(caughtType)?.let { constraint ->
          result.add(DfaInstructionState(runner.getInstruction(jumpOffset.instructionOffset), stateForCatchClause(param, constraint)))
        }

        throwableType = throwableType?.withNotInstanceofValue(caughtType) ?: return result
      }
    }
    return result + dispatch()
  }

  private fun allCaughtTypes(param: PsiParameter): List<DfaPsiType> {
    val psiTypes = param.type.let { if (it is PsiDisjunctionType) it.disjunctions else listOfNotNull(it) }
    return psiTypes.map { runner.factory.createDfaType(it) }
  }

  private fun stateForCatchClause(param: PsiParameter, constraint: TypeConstraint): DfaMemoryState {
    val catchingCopy = state.createCopy()
    val value = runner.factory.varFactory.createVariableValue(param)
    catchingCopy.applyFact(value, DfaFactType.TYPE_CONSTRAINT, constraint)
    catchingCopy.applyFact(value, DfaFactType.CAN_BE_NULL, false)
    return catchingCopy
  }

}
