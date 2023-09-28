// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.fix

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getUastParentOfType

class ReplaceCallableExpressionQuickFix(@SafeFieldForPreview private val callReplacementInfo: CallReplacementInfo) : PsiUpdateModCommandQuickFix() {
  override fun getFamilyName(): String = CommonQuickFixBundle.message("fix.replace.with.x", callReplacementInfo)

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val uCall = element.getUastParentOfType<UCallExpression>() ?: return
    uCall.replaceWithCallChain(callReplacementInfo)
  }
}