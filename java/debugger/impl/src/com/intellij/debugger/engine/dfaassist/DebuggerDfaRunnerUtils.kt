// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist

import com.intellij.codeInspection.dataFlow.jvm.descriptors.AssertionDisabledDescriptor
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow
import com.intellij.codeInspection.dataFlow.lang.ir.DataFlowIRProvider
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.dfaassist.DebuggerDfaRunner.Larva
import com.intellij.debugger.engine.dfaassist.DebuggerDfaRunner.Pupa
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.StackFrameProxyEx
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.impl.dfaassist.DfaResult
import com.sun.jdi.*
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ExecutionException


@Throws(EvaluateException::class)
private suspend fun hatch(proxy: StackFrameProxyEx, pointer: SmartPsiElementPointer<PsiElement?>): Larva? {
  val project = pointer.project
  val (e, provider) = constrainedReadAction(ReadConstraint.withDocumentsCommitted(project)) {
    val element = pointer.element ?: return@constrainedReadAction null
    if (!element.isValid) return@constrainedReadAction null
    if (isDumb(project)) return@constrainedReadAction null
    val provider = DfaAssistProvider.EP_NAME.forLanguage(element.getLanguage()) ?: return@constrainedReadAction null
    element to provider
  } ?: return null

  try {
    val match = syncReadAction { provider.locationMatches(e, proxy.location()) }
    if (!match) return null
  }
  catch (iea: IllegalArgumentException) {
    throw EvaluateException(iea.message, iea)
  }


  val (anchor, flow, factory, body, modificationStamp, offset, dfaVariableValues) =
    constrainedReadAction(ReadConstraint.withDocumentsCommitted(project)) {
      val element = pointer.element ?: return@constrainedReadAction null
      if (!element.isValid) return@constrainedReadAction null
      val anchor = provider.getAnchor(element) ?: return@constrainedReadAction null
      val body = provider.getCodeBlock(anchor) ?: return@constrainedReadAction null
      val factory = DfaValueFactory(project)
      val flow = DataFlowIRProvider.forElement(body, factory) ?: return@constrainedReadAction null
      val modificationStamp = PsiModificationTracker.getInstance(project).getModificationCount()
      val offset = flow.getStartOffset(anchor).getInstructionOffset()
      if (offset < 0) return@constrainedReadAction null
      val descriptors = flow.instructions
        .flatMap { it.getRequiredDescriptors(factory) }
        .toSet()
      val dfaVariableValues = factory.values.toList().asSequence().flatMap { dfaValue ->
        descriptors.asSequence().map { it.createValue(factory, dfaValue) }.plus(dfaValue)
      }.filterIsInstance<DfaVariableValue>().distinct().toList()

      LarvaData(anchor, flow, factory, body, modificationStamp, offset, dfaVariableValues)
    } ?: return null
  val valueToVariableMapping = HashMap<Value, MutableList<DfaVariableValue>>()
  for (dfaVar in dfaVariableValues) {
    val jdiValue = resolveJdiValue(provider, anchor, proxy, dfaVar) ?: continue
    valueToVariableMapping.computeIfAbsent(jdiValue) { v -> ArrayList() }.add(dfaVar)
  }
  val jdiToDfa = valueToVariableMapping
  if (jdiToDfa.isEmpty()) return null
  return Larva(project, anchor, body, flow, factory, modificationStamp, provider, jdiToDfa, proxy, offset)
}

@Throws(EvaluateException::class)
private suspend fun resolveJdiValue(
  provider: DfaAssistProvider,
  anchor: PsiElement,
  proxy: StackFrameProxyEx,
  variableValue: DfaVariableValue,
): Value? {
  if (variableValue.descriptor is AssertionDisabledDescriptor) {
    val location = proxy.location()
    val status = DebuggerUtilsEx.getEffectiveAssertionStatus(location)
    // Assume that assertions are enabled if we cannot fetch the status
    return location.virtualMachine().mirrorOf(status == ThreeState.NO)
  }
  return syncReadAction { provider.getJdiValueForDfaVariable(proxy, variableValue, anchor) }
}

private suspend fun makePupa(proxy: StackFrameProxyEx, pointer: SmartPsiElementPointer<PsiElement?>): Pupa? {
  val larva = try {
    hatch(proxy, pointer)
  }
  catch (_: VMDisconnectedException) {
    null
  }
  catch (_: VMOutOfMemoryException) {
    null
  }
  catch (_: InternalException) {
    null
  }
  catch (_: EvaluateException) {
    null
  }
  catch (_: InconsistentDebugInfoException) {
    null
  }
  catch (_: InvalidStackFrameException) {
    null
  }

  if (larva == null) return null
  return try {
    larva.pupate()
  }
  catch (_: VMDisconnectedException) {
    null
  }
  catch (_: VMOutOfMemoryException) {
    null
  }
  catch (_: InternalException) {
    null
  }
  catch (_: EvaluateException) {
    null
  }
  catch (_: InconsistentDebugInfoException) {
    null
  }
  catch (_: InvalidStackFrameException) {
    null
  }
}

@VisibleForTesting
suspend fun createDfaRunner(
  proxy: StackFrameProxyEx,
  pointer: SmartPsiElementPointer<PsiElement?>,
): DebuggerDfaRunner? {
  val pupa = makePupa(proxy, pointer) ?: return null
  return constrainedReadAction(ReadConstraint.withDocumentsCommitted(pointer.getProject())) {
    pupa.transform()
  }
}

internal fun scheduleDfaUpdate(assist: DfaAssist, newContext: DebuggerContextImpl, element: PsiElement) {
  val pointer = SmartPointerManager.createPointer<PsiElement?>(element)
  newContext.getManagerThread()!!.schedule(object : SuspendContextCommandImpl(newContext.suspendContext) {
    override suspend fun contextActionSuspend(suspendContext: SuspendContextImpl) {
      val proxy = suspendContext.getFrameProxy()
      if (proxy == null) {
        assist.cleanUp()
        return
      }
      val runnerPupa = makePupa(proxy, pointer)
      if (runnerPupa == null) {
        assist.cleanUp()
        return
      }
      blockingContext {
        val computation = ReadAction.nonBlocking<DfaResult> {
          runnerPupa.transform()?.computeHints() ?: DfaResult.EMPTY
        }
          .withDocumentsCommitted(suspendContext.debugProcess.project)
          .coalesceBy(assist)
          .finishOnUiThread(ModalityState.nonModal()) { hints -> assist.displayInlaysInternal(hints) }
          .submit(AppExecutorUtil.getAppExecutorService())
        assist.setComputation(computation)
      }
    }
  })
}

private data class LarvaData(
  val anchor: PsiElement,
  val flow: ControlFlow,
  val factory: DfaValueFactory,
  val body: PsiElement,
  val modificationStamp: Long,
  val offset: Int,
  val dfaVariableValues: List<DfaVariableValue>,
)

private suspend fun <T> syncReadAction(action: () -> T): T = blockingContext {
  try {
    ReadAction.nonBlocking(action).executeSynchronously()
  }
  catch (e: ExecutionException) {
    throw e.cause ?: e
  }
}
