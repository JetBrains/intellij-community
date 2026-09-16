// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.lang.jvm.actions.JvmGroupModCommandAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiImplicitClass
import com.intellij.psi.PsiMethod

internal abstract class CreateFieldActionBase(
  target: PsiClass,
  override val request: CreateFieldRequest,
) : CreateMemberModCommandAction(target, request), JvmGroupModCommandAction {

  override fun getRenderData(): JvmActionGroup.RenderData = JvmActionGroup.RenderData { request.fieldName }

  override fun getTarget(): JvmClass? = targetClass

  override fun getFamilyName(): String = message("create.field.from.usage.family")

  override fun isAvailable(target: PsiClass): Boolean {
    if (target.findFieldByName(request.fieldName, false) != null) return false
    return isClassBodyValid(target)
  }

  private fun isClassBodyValid(target: PsiClass): Boolean {
    if (target !is PsiImplicitClass) return true
    if (target.lastChild is PsiErrorElement) return false
    return target.children
      .asSequence()
      .filterIsInstance<PsiMethod>()
      .mapNotNull { it.body }
      .none { it.lastChild is PsiErrorElement }
  }

  internal open fun isConstant(): Boolean = false

  override fun invoke(context: ActionContext, element: PsiClass, updater: ModPsiUpdater) {
    val originalTarget = targetClass ?: return
    JavaFieldRenderer(context.project, isConstant(), element, originalTarget, request, updater).doRender()
  }
}
