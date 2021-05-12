// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package com.intellij.codeInspection.dataFlow.jvm

import com.intellij.codeInspection.dataFlow.java.inst.ControlTransferInstruction
import com.intellij.codeInspection.dataFlow.jvm.transfer.ExceptionTransfer
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue
import com.intellij.psi.*

sealed class JvmTrap(private val anchor: PsiElement) : DfaControlTransferValue.Trap {
  open fun link(instruction: ControlTransferInstruction) {}

  abstract fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState>
  override fun toString(): String = javaClass.simpleName!!
  override fun getAnchor(): PsiElement = anchor

  class TryCatch(tryStatement: PsiTryStatement, val clauses: LinkedHashMap<PsiCatchSection, ControlFlow.ControlFlowOffset>)
    : JvmTrap(tryStatement) {
    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> {
      val target = handler.target
      return if (target is ExceptionTransfer) handler.processCatches(target.throwable, clauses)
      else handler.doDispatch()
    }

    override fun getPossibleTargets() = clauses.values.map { it.instructionOffset }
    override fun toString(): String = "${super.toString()} -> ${clauses.values}"
  }
  class TryCatchAll(anchor: PsiElement, val target : ControlFlow.ControlFlowOffset)
    : JvmTrap(anchor) {
    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> {
      return if (handler.target is ExceptionTransfer)
        listOf(DfaInstructionState(
          handler.runner.getInstruction(target.instructionOffset), handler.state))
      else handler.doDispatch()
    }

    override fun getPossibleTargets() = listOf(target.instructionOffset)
    override fun toString(): String = "${super.toString()} -> ${target}"
  }
  abstract class EnterFinally(anchor: PsiElement, val jumpOffset: ControlFlow.ControlFlowOffset): JvmTrap(anchor) {
    internal val backLinks = ArrayList<ControlTransferInstruction>()

    override fun link(instruction: ControlTransferInstruction) {
      backLinks.add(instruction)
    }
    
    fun backLinks() : List<ControlTransferInstruction> = backLinks

    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> {
      handler.state.push(handler.runner.factory.controlTransfer(handler.target, handler.traps))
      return listOf(DfaInstructionState(
        handler.runner.getInstruction(jumpOffset.instructionOffset), handler.state))
    }

    override fun getPossibleTargets() = listOf(jumpOffset.instructionOffset)
    override fun toString(): String = "${super.toString()} -> $jumpOffset"
  }
  class TryFinally(finallyBlock: PsiCodeBlock, jumpOffset: ControlFlow.ControlFlowOffset): EnterFinally(finallyBlock, jumpOffset)
  class TwrFinally(resourceList: PsiResourceList, jumpOffset: ControlFlow.ControlFlowOffset) : EnterFinally(resourceList, jumpOffset) {
    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> =
      if (handler.target is ExceptionTransfer) handler.doDispatch()
      else super.dispatch(handler)
  }
  class InsideFinally(finallyBlock: PsiElement): JvmTrap(finallyBlock) {
    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> {
      handler.state.pop() as DfaControlTransferValue
      return handler.doDispatch()
    }
  }
  class InsideInlinedBlock(block: PsiCodeBlock): JvmTrap(block) {
    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> {
      if ((handler.state.pop() as DfaControlTransferValue).target !== DfaControlTransferValue.RETURN_TRANSFER) {
        throw IllegalStateException()
      }
      return handler.doDispatch()
    }
  }
}
