// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.lang.jvm.actions.CreateConstantActionGroup
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.psi.PsiClass
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.JavaElementKind

/**
 * This action renders a static final field.
 */
internal class CreateConstantAction(
  target: PsiClass,
  request: CreateFieldRequest,
) : CreateFieldActionBase(target, request) {

  override fun getActionGroup(): JvmActionGroup = CreateConstantActionGroup

  override val priority: PriorityAction.Priority get() = PriorityAction.Priority.HIGH

  override fun isConstant(): Boolean = true

  override fun getText(target: PsiClass): String = message("create.element.in.class", JavaElementKind.CONSTANT.`object`(),
                                                            request.fieldName, getNameForClass(target, false))
}
