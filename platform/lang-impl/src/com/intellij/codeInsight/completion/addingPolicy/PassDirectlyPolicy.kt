// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.addingPolicy

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement

/**
 * Pass all elements directly to the result set
 */
class PassDirectlyPolicy : ElementsAddingPolicy.Default {
  override fun addElement(result: CompletionResultSet, element: LookupElement) {
    result.addElement(element)
  }

  override fun addAllElements(result: CompletionResultSet, elements: Iterable<LookupElement>) {
    result.addAllElements(elements)
  }
}