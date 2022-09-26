// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.psi.psiDocumentationTarget
import com.intellij.lang.documentation.symbol.impl.symbolDocumentationTargets
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.asSafely

open class IdeDocumentationTargetProviderImpl(private val project: Project) : IdeDocumentationTargetProvider {

  override fun documentationTarget(editor: Editor, file: PsiFile, lookupElement: LookupElement): DocumentationTarget? {
    val symbolTargets = (lookupElement.`object` as? Pointer<*>)
      ?.dereference()
      ?.asSafely<Symbol>()
      ?.let { symbolDocumentationTargets(file.project, listOf(it)) }
    if (!symbolTargets.isNullOrEmpty()) {
      return symbolTargets.first()
    }
    val sourceElement = file.findElementAt(editor.caretModel.offset)
    val targetElement = DocumentationManager.getElementFromLookup(project, editor, file, lookupElement)
                        ?: return null
    return psiDocumentationTarget(targetElement, sourceElement)
  }

  override fun documentationTargets(editor: Editor, file: PsiFile, offset: Int): List<DocumentationTarget> {
    return com.intellij.lang.documentation.impl.documentationTargets(file, offset)
  }
}
