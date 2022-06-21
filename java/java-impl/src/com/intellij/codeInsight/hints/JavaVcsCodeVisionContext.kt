// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.daemon.impl.JavaCodeVisionUsageCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypeParameter
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
}