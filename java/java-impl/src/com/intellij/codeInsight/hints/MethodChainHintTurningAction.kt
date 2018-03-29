// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

class MethodChainHintTurningAction : PsiElementBaseIntentionAction() {
  override fun getFamilyName() = "Turn method chain hints"

  override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    CodeInsightSettings.getInstance().SHOW_METHOD_CHAIN_TYPES_INLINE = !CodeInsightSettings.getInstance().SHOW_METHOD_CHAIN_TYPES_INLINE
    AnnotationHintsPassFactory.forceHintsUpdateOnNextPass()
  }

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    val origin = if (element is PsiWhiteSpace) {
      PsiTreeUtil.skipWhitespacesBackward(element) ?: return false
    } else {
      element
    }
    val parent = PsiTreeUtil.getParentOfType(origin, PsiMethodCallExpression::class.java)
    if (parent !is PsiMethodCallExpression) return false
    val available = (parent.nextSibling as? PsiWhiteSpace)?.textContains('\n') ?: false
    text = if (CodeInsightSettings.getInstance().SHOW_METHOD_CHAIN_TYPES_INLINE) {
      ApplicationBundle.message("editor.appearance.show.chain.call.type.hints.toggle.off")
    }
    else {
      ApplicationBundle.message("editor.appearance.show.chain.call.type.hints.toggle.on")
    }
    return available
  }

}