// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

class GenerateLogFieldHandler : CodeInsightActionHandler {
  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val currentElement = file.findElementAt(editor.caretModel.offset)
    PsiTreeUtil.getTopmostParentOfType(currentElement, PsiClass::class.java)

  }
}