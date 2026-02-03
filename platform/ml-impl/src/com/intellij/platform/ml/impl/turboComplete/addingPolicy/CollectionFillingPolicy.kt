// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.turboComplete.addingPolicy

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.addingPolicy.ElementsAddingPolicy
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.annotations.ApiStatus

/**
 * Fill the given collection, when an element is added
 */
@ApiStatus.Internal
class CollectionFillingPolicy(private val addedElements: MutableCollection<LookupElement>) : ElementsAddingPolicy.Default {

  override fun addElement(result: CompletionResultSet, element: LookupElement) {
    addedElements.add(element)
  }

  override fun addAllElements(result: CompletionResultSet, elements: Iterable<LookupElement>) {
    addedElements.addAll(elements)
  }
}