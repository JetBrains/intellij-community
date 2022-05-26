// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.lang.documentation.psi.psiDocumentationTarget
import com.intellij.lang.documentation.symbol.impl.symbolDocumentationTargets
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.PsiFile
import com.intellij.util.castSafelyTo

open class IdeDocumentationTargetProviderImpl(private val project: Project) : IdeDocumentationTargetProvider {

  override fun documentationTarget(editor: Editor, file: PsiFile, lookupElement: LookupElement): DocumentationTarget? {
    val symbolTargets = (lookupElement.`object` as? Pointer<*>)
      ?.dereference()
      ?.castSafelyTo<Symbol>()
      ?.let { symbolDocumentationTargets(file.project, listOf(it)) }
    if (symbolTargets != null && symbolTargets.isNotEmpty()) {
      return symbolTargets.first()
    }
    val targetElement = DocumentationManager.getElementFromLookup(project, editor, file, lookupElement)
                        ?: return null
    psiDocumentationTarget(targetElement)?.let {
      return it
    }
    val sourceElement = file.findElementAt(editor.caretModel.offset)
    return PsiElementDocumentationTarget(project, targetElement, sourceElement, anchor = null)
  }

  override fun documentationTargets(editor: Editor, file: PsiFile, offset: Int): List<DocumentationTarget> {
    val symbolTargets = symbolDocumentationTargets(file, offset)
    if (symbolTargets.isNotEmpty()) {
      return symbolTargets
    }
    val documentationManager = DocumentationManager.getInstance(project)
    val (targetElement, sourceElement) = documentationManager.findTargetElementAndContext(editor, offset, file)
                                         ?: return emptyList()
    psiDocumentationTarget(targetElement)?.let {
      return listOf(it)
    }
    return listOf(PsiElementDocumentationTarget(project, targetElement, sourceElement, anchor = null))
  }
}
