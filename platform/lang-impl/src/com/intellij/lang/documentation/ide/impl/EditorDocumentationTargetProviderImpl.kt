// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.ide.EditorDocumentationTargetProvider
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.PsiFile

class EditorDocumentationTargetProviderImpl : EditorDocumentationTargetProvider {

  override fun documentationTargets(editor: Editor, file: PsiFile, offset: Int): List<DocumentationTarget> {
    val project = file.project
    val documentationManager = DocumentationManager.getInstance(project)
    val (targetElement, sourceElement) = documentationManager.findTargetElementAndContext(editor, offset, file) ?: return emptyList()
    return listOf(PsiElementDocumentationTarget(project, targetElement, sourceElement, anchor = null))
  }
}
