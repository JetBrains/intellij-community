// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.lang.jvm.actions.JvmGroupIntentionAction
import com.intellij.psi.PsiClass

internal abstract class CreateFieldActionBase(
  target: PsiClass,
  override val request: CreateFieldRequest
) : CreateMemberAction(target, request), JvmGroupIntentionAction {

  override fun getRenderData() = JvmActionGroup.RenderData { request.fieldName }

  override fun getFamilyName(): String = message("create.field.from.usage.family")
}
