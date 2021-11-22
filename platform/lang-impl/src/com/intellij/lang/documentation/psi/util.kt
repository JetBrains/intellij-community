// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.psi

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.PsiFile

@JvmField
internal val LOG: Logger = Logger.getInstance("#com.intellij.lang.documentation.psi")

internal fun psiDocumentationTarget(editor: Editor, file: PsiFile, offset: Int): DocumentationTarget? {
  return psiDocumentationTarget(file.project, editor, file, offset)
}

internal fun psiDocumentationTarget(project: Project, editor: Editor, file: PsiFile, offset: Int): DocumentationTarget? {
  val documentationManager = DocumentationManager.getInstance(project)
  val (targetElement, sourceElement) = documentationManager.findTargetElementAndContext(editor, offset, file) ?: return null
  return PsiElementDocumentationTarget(project, targetElement, sourceElement, anchor = null)
}
