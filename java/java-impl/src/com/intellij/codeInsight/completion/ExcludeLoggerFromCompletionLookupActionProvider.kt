// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupActionProvider
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementAction
import com.intellij.psi.PsiElement
import com.intellij.util.Consumer

class ExcludeLoggerFromCompletionLookupActionProvider : LookupActionProvider {
  override fun fillActions(lookupElement: LookupElement, lookup: Lookup, consumer: Consumer<in LookupElementAction>) {
    val psiElement = lookupElement.psiElement
    if (lookupElement is LoggerLookupElement && psiElement is PsiElement) {
      ExcludeFromCompletionLookupActionProvider.addExcludes(consumer, psiElement, lookupElement.loggerTypeName)
    }
  }
}