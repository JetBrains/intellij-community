// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaDebuggerMagicConstantUtils")

package com.intellij.debugger.engine

import com.intellij.codeInspection.magicConstant.MagicConstantUtils
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.ui.impl.watch.ArgumentValueDescriptorImpl
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl
import com.intellij.debugger.ui.impl.watch.MethodReturnValueDescriptorImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.FieldDescriptor
import com.intellij.debugger.ui.tree.LocalVariableDescriptor
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.sun.jdi.ByteValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongValue
import com.sun.jdi.ShortValue
import com.sun.jdi.Value

/**
 * If the value held by [descriptor] is an integer-like primitive and the declaration it came from is annotated
 * with `@MagicConstant`, schedules a coroutine that computes the symbolic name (e.g. `BOLD`) and appends it to
 * [rawLabel] via [ValueDescriptorImpl.setValueLabel] + [DescriptorLabelListener.labelChanged].
 *
 * The coroutine touches JDI on the debugger manager thread and PSI under a non-blocking read action,
 * never blocking either thread for the other's work. It is canceled automatically if the suspend context resumes.
 *
 * If the value does not match any magic constant (or PSI resolution fails), nothing is updated.
 */
fun scheduleMagicConstantSuffix(
  descriptor: ValueDescriptor,
  evaluationContext: EvaluationContextImpl,
  rawLabel: String,
  labelListener: DescriptorLabelListener,
) {
  val value = descriptor.value ?: return
  val boxed = boxIntegralValue(value) ?: return
  val suspendContext = evaluationContext.suspendContext
  val project = evaluationContext.project

  executeOnDMT(suspendContext, PrioritizedTask.Priority.LOW) {
    // On the debugger manager thread: read everything we need from JDI into plain data.
    val info = extractOwnerInfo(descriptor, suspendContext) ?: return@executeOnDMT

    // Off the manager thread, under a non-blocking read action: resolve PSI and consult MagicConstantUtils.
    val magicText = readAction {
      val owner = resolveOwner(info, project) ?: return@readAction null
      MagicConstantUtils.getPresentableText(boxed, owner)
    }
    if (magicText.isNullOrEmpty()) return@executeOnDMT

    // Back on the manager thread: guard against a stale value and publish the new label.
    if (descriptor.value !== value) return@executeOnDMT
    descriptor.setValueLabel("$rawLabel ($magicText)")
    labelListener.labelChanged()
  }
}

private fun boxIntegralValue(value: Value): Number? = when (value) {
  is IntegerValue -> value.value()
  is LongValue -> value.value()
  is ShortValue -> value.value()
  is ByteValue -> value.value()
  else -> null
}

private fun extractOwnerInfo(
  descriptor: ValueDescriptor,
  suspendContext: SuspendContextImpl,
): OwnerInfo? {
  DebuggerManagerThreadImpl.assertIsManagerThread()
  return when (descriptor) {
    is LocalVariableDescriptor ->
      currentSourcePosition(suspendContext)?.let { OwnerInfo.Local(descriptor.name, it) }
    is ArgumentValueDescriptorImpl -> {
      val name = descriptor.variable?.matchedNames?.firstOrNull() ?: return null
      currentSourcePosition(suspendContext)?.let { OwnerInfo.Local(name, it) }
    }
    is FieldDescriptor -> {
      val field = descriptor.field
      val fieldName = field.name()
      if (fieldName.startsWith(FieldDescriptorImpl.OUTER_LOCAL_VAR_FIELD_PREFIX)) {
        val position = currentSourcePosition(suspendContext) ?: return null
        OwnerInfo.OuterLocalField(fieldName.substringAfterLast('$'), position)
      }
      else {
        val typeName = field.declaringType().name()
        val scope = suspendContext.debugProcess.session?.searchScope
        OwnerInfo.Field(fieldName, typeName, scope)
      }
    }
    is MethodReturnValueDescriptorImpl -> {
      val position = suspendContext.debugProcess.positionManager.getSourcePosition(descriptor.method.location()) ?: return null
      OwnerInfo.Method(position)
    }
    else -> null
  }
}

private fun currentSourcePosition(suspendContext: SuspendContextImpl): SourcePosition? {
  val frame = suspendContext.frameProxy ?: return null
  val location = try {
    frame.location()
  }
  catch (_: Throwable) {
    return null
  }
  return suspendContext.debugProcess.positionManager.getSourcePosition(location)
}

private fun resolveOwner(info: OwnerInfo, project: Project): PsiModifierListOwner? = when (info) {
  is OwnerInfo.Local -> {
    val place = info.position.elementAt
    if (place == null) null
    else JavaPsiFacade.getInstance(project).resolveHelper.resolveReferencedVariable(info.name, place)
  }
  is OwnerInfo.Field -> {
    val scope = info.scope ?: GlobalSearchScope.allScope(project)
    DebuggerUtils.findClass(info.typeName, project, scope)?.findFieldByName(info.fieldName, false)
  }
  is OwnerInfo.OuterLocalField -> {
    val element = info.position.elementAt
    val outerClass = element?.let { PsiTreeUtil.getParentOfType(it, PsiClass::class.java, false) }
    val navigation = outerClass?.navigationElement as? PsiClass
    navigation?.let {
      JavaPsiFacade.getInstance(project).resolveHelper.resolveReferencedVariable(info.varName, it)
    }
  }
  is OwnerInfo.Method -> {
    val element = info.position.elementAt
    element?.let { PsiTreeUtil.getParentOfType(it, PsiMethod::class.java, false) }
  }
}?.takeIf { it.containingFile != null }

private sealed interface OwnerInfo {
  data class Local(val name: String, val position: SourcePosition) : OwnerInfo
  data class Field(val fieldName: String, val typeName: String, val scope: GlobalSearchScope?) : OwnerInfo
  data class OuterLocalField(val varName: String, val position: SourcePosition) : OwnerInfo
  data class Method(val position: SourcePosition) : OwnerInfo
}
