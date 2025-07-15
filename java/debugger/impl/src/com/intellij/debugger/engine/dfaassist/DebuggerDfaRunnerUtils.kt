// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist

import com.intellij.codeInspection.dataFlow.jvm.descriptors.AssertionDisabledDescriptor
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow
import com.intellij.codeInspection.dataFlow.lang.ir.DataFlowIRProvider
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor
import com.intellij.debugger.engine.dfaassist.DebuggerDfaRunner.Larva
import com.intellij.debugger.engine.dfaassist.DebuggerDfaRunner.Pupa
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.executeOnDMT
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.StackFrameProxyEx
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ThreeState
import com.intellij.xdebugger.impl.dfaassist.DfaResult
import com.sun.jdi.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting


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
    val match = provider.locationMatches(e, proxy.location())
    if (!match) return null
  }
  catch (iea: IllegalArgumentException) {
    throw EvaluateException(iea.message, iea)
  }


  val (anchor, flow, factory, body, 
    modificationStamp, offset, descriptors, qualifiers) =
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
      val qualifiers = factory.values.filterIsInstance<DfaVariableValue>()

      LarvaData(anchor, flow, factory, body, modificationStamp, offset, descriptors, qualifiers)
    } ?: return null
  if (isDumb(project)) return null
  val possiblyQualifiedDescriptors = arrayListOf<VariableDescriptor>()
  val varToValueMap = hashMapOf<DfaVariableValue, Value>()
  for (descriptor in descriptors) {
    if (descriptor is AssertionDisabledDescriptor) {
      val location = proxy.location()
      val status = DebuggerUtilsEx.getEffectiveAssertionStatus(location)
      // Assume that assertions are enabled if we cannot fetch the status
      val jdiValue = location.virtualMachine().mirrorOf(status == ThreeState.NO)
      val dfaVar = readAction { factory.varFactory.createVariableValue(descriptor) }
      varToValueMap[dfaVar] = jdiValue
      continue
    }
    val unqualifiedValue = provider.getJdiValueForDfaVariable(proxy, descriptor, anchor)
    if (unqualifiedValue != null) {
      val dfaVar = readAction { factory.varFactory.createVariableValue(descriptor) }
      varToValueMap[dfaVar] = unqualifiedValue
      continue
    }
    possiblyQualifiedDescriptors.add(descriptor)
  }
  if (possiblyQualifiedDescriptors.isNotEmpty() && varToValueMap.isNotEmpty()) {
    // Sort qualifiers by depth to ensure that previous qualifiers are already processed
    for (qualifier in qualifiers.sortedBy { it.depth }) {
      val jdiQualifier = varToValueMap[qualifier] ?: continue
      val map = provider.getJdiValuesForQualifier(proxy, jdiQualifier, possiblyQualifiedDescriptors, anchor)
      for ((descriptor, jdiValue) in map) {
        val dfaVar = readAction { descriptor.createValue(factory, qualifier) as? DfaVariableValue } ?: continue
        varToValueMap[dfaVar] = jdiValue
      }
    }
  }
  if (varToValueMap.isEmpty()) return null
  val valueToVariableMapping = varToValueMap.entries
    .asSequence()
    .filter { it.value !is DfaAssistProvider.InlinedValue }
    .groupBy({ it.value }, { it.key })
  return Larva(project, anchor, body, flow, factory, modificationStamp, provider, valueToVariableMapping, proxy, offset)
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
  catch (_: IndexNotReadyException) {
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
  catch (_: IndexNotReadyException) {
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
  val suspendContext = newContext.suspendContext ?: return
  executeOnDMT(suspendContext) {
    val proxy = suspendContext.getFrameProxy()
    if (proxy == null) {
      assist.cleanUp()
      return@executeOnDMT
    }
    val runnerPupa = makePupa(proxy, pointer)
    if (runnerPupa == null) {
      assist.cleanUp()
      return@executeOnDMT
    }
    val project = suspendContext.debugProcess.project
    val hintsJob = suspendContext.coroutineScope.async {
      constrainedReadAction(ReadConstraint.withDocumentsCommitted(project)) {
        runnerPupa.transform()?.computeHints() ?: DfaResult.EMPTY
      }
    }
    assist.cancelComputation()
    assist.setComputation(hintsJob)
    val hints = hintsJob.await()
    withContext(Dispatchers.EDT) {
      assist.displayInlaysInternal(hints)
    }
  }
}

private data class LarvaData(
  val anchor: PsiElement,
  val flow: ControlFlow,
  val factory: DfaValueFactory,
  val body: PsiElement,
  val modificationStamp: Long,
  val offset: Int,
  val descriptors: Collection<VariableDescriptor>,
  val qualifiers: List<DfaVariableValue>,
)
