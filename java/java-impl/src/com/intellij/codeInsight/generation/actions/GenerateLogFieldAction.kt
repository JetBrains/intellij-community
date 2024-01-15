// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.actions

import com.intellij.codeInsight.generation.GenerateLogFieldHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

class GenerateLogFieldAction : BaseGenerateAction(GenerateLogFieldHandler()) {

  override fun isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean {
    val element = file.findElementAt(editor.caretModel.offset)
    return PsiTreeUtil.getParentOfType(element, PsiClass::class.java) != null
  }
}