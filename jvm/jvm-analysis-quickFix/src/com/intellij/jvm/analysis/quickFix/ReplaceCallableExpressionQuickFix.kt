// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jvm.analysis.quickFix

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.jvm.analysis.refactoring.CallChainReplacementInfo
import com.intellij.jvm.analysis.refactoring.replaceWithCallChain
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getUastParentOfType

class ReplaceCallableExpressionQuickFix(private val callChainReplacementInfo: CallChainReplacementInfo) : PsiUpdateModCommandQuickFix() {
  override fun getFamilyName(): String = CommonQuickFixBundle.message("fix.replace.with.x", callChainReplacementInfo.presentation)

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val uCall = element.getUastParentOfType<UCallExpression>() ?: return
    uCall.replaceWithCallChain(callChainReplacementInfo)
  }
}