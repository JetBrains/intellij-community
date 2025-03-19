// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.model.psi.PsiCompletableReference
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.impl.referencesAt
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ReferenceBasedCompletionContributor : CompletionContributor(), DumbAware {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.completionType != CompletionType.BASIC) {
      return
    }

    val fileOffset: Int = parameters.offset

    val references: Collection<PsiSymbolReference>
    try {
      references = parameters.position.containingFile.referencesAt(fileOffset)
    }
    catch (_: IndexNotReadyException) {
      return
    }

    for (reference: PsiSymbolReference in references) {
      ProgressManager.checkCanceled()

      if (reference !is PsiCompletableReference) {
        continue
      }

      val variants: Collection<LookupElement>
      try {
        variants = reference.completionVariants
      }
      catch (_: IndexNotReadyException) {
        continue
      }

      if (variants.isEmpty()) {
        continue
      }

      ProgressManager.checkCanceled()

      val element: PsiElement = reference.getElement()
      val beginIndex: Int = reference.rangeInElement.startOffset
      val offsetInElement: Int = fileOffset - element.textRange.startOffset
      val prefix: String = element.text.substring(beginIndex, offsetInElement)
      val resultWithPrefix: CompletionResultSet = result.withPrefixMatcher(prefix)

      for (variant: LookupElement in variants) {
        resultWithPrefix.addElement(variant)
      }
    }
  }
}
