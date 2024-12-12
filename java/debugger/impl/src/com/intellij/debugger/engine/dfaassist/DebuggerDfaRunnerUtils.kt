// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist

import com.intellij.codeInspection.dataFlow.jvm.descriptors.AssertionDisabledDescriptor
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow
import com.intellij.codeInspection.dataFlow.lang.ir.DataFlowIRProvider
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor
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
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xdebugger.impl.dfaassist.DfaResult
import com.sun.jdi.*
import one.util.streamex.StreamEx
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Consumer


@RequiresReadLock
@Throws(EvaluateException::class)
private fun hatch(proxy: StackFrameProxyEx, element: PsiElement?): Larva? {
  if (element == null || !element.isValid()) return null
  val project = element.getProject()
  if (isDumb(project)) return null

  val provider = DfaAssistProvider.EP_NAME.forLanguage(element.getLanguage()) ?: return null
  try {
    if (!provider.locationMatches(element, proxy.location())) return null
  }
  catch (iea: IllegalArgumentException) {
    throw EvaluateException(iea.message, iea)
  }
  val anchor = provider.getAnchor(element) ?: return null
  val body = provider.getCodeBlock(anchor) ?: return null
  val factory = DfaValueFactory(project)
  val flow = DataFlowIRProvider.forElement(body, factory) ?: return null
  val modificationStamp = PsiModificationTracker.getInstance(project).getModificationCount()
  val offset = flow.getStartOffset(anchor).getInstructionOffset()
  if (offset < 0) return null
  val jdiToDfa = createPreliminaryJdiMap(provider, anchor, flow, proxy)
  if (jdiToDfa.isEmpty()) return null
  return Larva(project, anchor, body, flow, factory, modificationStamp, provider, jdiToDfa, proxy, offset)
}

@Throws(EvaluateException::class)
private fun createPreliminaryJdiMap(
  provider: DfaAssistProvider,
  anchor: PsiElement,
  flow: ControlFlow,
  proxy: StackFrameProxyEx,
): MutableMap<Value, MutableList<DfaVariableValue?>> {
  val factory = flow.factory
  val descriptors = StreamEx.of<Instruction?>(*flow.instructions)
    .flatCollection<VariableDescriptor?> { it.getRequiredDescriptors(factory) }
    .toSet()
  val myMap = HashMap<Value, MutableList<DfaVariableValue?>>()
  val stream = StreamEx.of<DfaValue?>(*factory.values.toTypedArray())
    .flatMap<DfaValue> { dfaValue ->
      StreamEx.of<VariableDescriptor?>(descriptors).map<DfaValue> { desc ->
        desc.createValue(factory, dfaValue)
      }.append(dfaValue)
    }
    .select<DfaVariableValue?>(DfaVariableValue::class.java)
    .distinct()
  for (dfaVar in stream) {
    val jdiValue = resolveJdiValue(provider, anchor, proxy, dfaVar) ?: continue
    myMap.computeIfAbsent(jdiValue) { v -> ArrayList<DfaVariableValue?>() }.add(dfaVar)
  }
  return myMap
}

@Throws(EvaluateException::class)
private fun resolveJdiValue(
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
  return provider.getJdiValueForDfaVariable(proxy, variableValue, anchor)
}

private fun makePupa(proxy: StackFrameProxyEx, pointer: SmartPsiElementPointer<PsiElement?>): Pupa? {
  val project = pointer.getProject()
  val larva = ReadAction.nonBlocking<Larva?> {
    try {
      hatch(proxy, pointer.getElement())
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
  }.withDocumentsCommitted(project).executeSynchronously() ?: return null

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
fun createDfaRunner(
  proxy: StackFrameProxyEx,
  pointer: SmartPsiElementPointer<PsiElement?>,
): DebuggerDfaRunner? {
  val pupa = makePupa(proxy, pointer) ?: return null
  return ReadAction.nonBlocking<DebuggerDfaRunner?> { pupa.transform() }
    .withDocumentsCommitted(pointer.getProject())
    .executeSynchronously()
}

internal fun scheduleDfaUpdate(assist: DfaAssist, newContext: DebuggerContextImpl, element: PsiElement) {
  val pointer = SmartPointerManager.createPointer<PsiElement?>(element)
  newContext.getManagerThread()!!.schedule(object : SuspendContextCommandImpl(newContext.suspendContext) {
    override fun contextAction(suspendContext: SuspendContextImpl) {
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
      val computation = ReadAction.nonBlocking<DfaResult> {
        runnerPupa.transform()?.computeHints() ?: DfaResult.EMPTY
      }
        .withDocumentsCommitted(suspendContext.debugProcess.project)
        .coalesceBy(assist)
        .finishOnUiThread(ModalityState.nonModal()) { hints -> assist.displayInlaysInternal(hints) }
        .submit(AppExecutorUtil.getAppExecutorService())
      assist.setComputation(computation)
    }
  })
}