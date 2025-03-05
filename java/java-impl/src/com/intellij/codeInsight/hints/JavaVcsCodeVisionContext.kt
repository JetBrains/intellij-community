// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.daemon.impl.JavaCodeVisionUsageCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import java.awt.event.MouseEvent

private class JavaVcsCodeVisionContext : VcsCodeVisionCurlyBracketLanguageContext() {
  override fun isAccepted(element: PsiElement): Boolean {
    return element is PsiMethod || (element is PsiClass && element !is PsiTypeParameter)
  }

  override fun handleClick(mouseEvent: MouseEvent, editor: Editor, element: PsiElement) {
    if (element !is PsiMember) error("Only members allowed for code vision, got: $element")
    val project = element.project
    val location = if (element is PsiClass) JavaCodeVisionUsageCollector.CLASS_LOCATION else JavaCodeVisionUsageCollector.METHOD_LOCATION

    JavaCodeVisionUsageCollector.logCodeAuthorClicked(project, location)
  }

  override fun isRBrace(element: PsiElement): Boolean {
    return PsiUtil.isJavaToken(element, JavaTokenType.RBRACE)
  }
}