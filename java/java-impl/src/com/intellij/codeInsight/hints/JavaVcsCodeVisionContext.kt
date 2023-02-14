// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.daemon.impl.JavaCodeVisionUsageCollector
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import java.awt.event.MouseEvent

class JavaVcsCodeVisionContext : VcsCodeVisionLanguageContext {
  override fun isAccepted(element: PsiElement): Boolean {
    return element is PsiMethod || (element is PsiClass && element !is PsiTypeParameter)
  }

  override fun handleClick(mouseEvent: MouseEvent, editor: Editor, element: PsiElement) {
    if (element !is PsiMember) error("Only members allowed for code vision, got: $element")
    val project = element.project
    val location = if (element is PsiClass) JavaCodeVisionUsageCollector.CLASS_LOCATION else JavaCodeVisionUsageCollector.METHOD_LOCATION

    JavaCodeVisionUsageCollector.logCodeAuthorClicked(project, location)
  }

  override fun trimInsignificantChildren(element: PsiElement): TextRange {
    val start = (element as? PsiNameIdentifierOwner)?.nameIdentifier ?: element
    val end = SyntaxTraverser.psiApiReversed().children(element.lastChild).firstOrNull {
      it !is PsiWhiteSpace && !PsiUtil.isJavaToken(it, JavaTokenType.RBRACE)
    } ?: element
    return TextRange.create(start.startOffset, end.endOffset)
  }
}