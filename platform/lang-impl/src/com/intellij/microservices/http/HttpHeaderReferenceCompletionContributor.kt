// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.http

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceUtil
import com.intellij.psi.util.parents
import com.intellij.util.ThreeState

internal class HttpHeaderReferenceCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val containingFile = parameters.position.containingFile ?: return
    val multiReference = containingFile.findReferenceAt(parameters.offset) ?: return

    PsiReferenceUtil.findReferenceOfClass(multiReference, HttpHeaderReference::class.java)?.let {
      for (headerEntry in HttpHeadersDictionary.getHeaders().entries) {
        ProgressManager.checkCanceled()

        result.consume(LookupElementBuilder.create(headerEntry.value, headerEntry.key)
                         .withIcon(AllIcons.Nodes.Constant))
      }
    }
  }
}

internal class EnableAutopopupInHttpHeaderReferences : CompletionConfidence() {
  override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
    if (hasHttpReferences(contextElement, offset)) return ThreeState.NO
    return super.shouldSkipAutopopup(editor, contextElement, psiFile, offset)
  }
}

internal class QuotesTypedHandlerAutoPopup : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (charTyped != '"') return Result.CONTINUE

    AutoPopupController.getInstance(project).scheduleAutoPopup(editor, CompletionType.BASIC) { f ->
      val offset = editor.caretModel.offset
      val psiElement = f.findElementAt(offset) ?: return@scheduleAutoPopup false
      hasHttpReferences(psiElement, offset)
    }
    return Result.CONTINUE
  }
}

private fun hasHttpReferences(contextElement: PsiElement, offset: Int): Boolean {
  val referenceHost = getReferenceHost(contextElement) ?: return false
  val references = referenceHost.references

  for (reference in references) {
    if (reference !is HttpHeaderReference && reference !is HttpMethodReference) continue
    if (reference.absoluteRange.containsOffset(offset))
      return true
  }
  return false
}

private fun getReferenceHost(contextElement: PsiElement): PsiElement? =
  contextElement.parents(true).take(3).firstOrNull { it is PsiLanguageInjectionHost }