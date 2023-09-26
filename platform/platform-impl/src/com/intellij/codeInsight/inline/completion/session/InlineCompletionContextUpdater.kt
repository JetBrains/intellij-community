// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.codeInsight.inline.completion.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent


internal sealed interface UpdateContextResult {
  class Changed(val newElements: List<InlineCompletionElement>, val truncateTyping: Int) : UpdateContextResult

  data object Same : UpdateContextResult

  data object Invalidated : UpdateContextResult
}

internal fun updateContext(context: InlineCompletionContext, event: InlineCompletionEvent): UpdateContextResult {
  return when (event) {
    is InlineCompletionEvent.DocumentChange -> {
      assert(event.event.oldLength == 0)
      val fragment = event.event.newFragment
      val (newElements, truncateTyping) = applyPrefixAppend(context, fragment) ?: return UpdateContextResult.Invalidated
      UpdateContextResult.Changed(newElements, truncateTyping)
    }
    is InlineCompletionEvent.LookupChange -> {
      if (context.isCurrentlyDisplayingInlays) UpdateContextResult.Same else UpdateContextResult.Invalidated
    }
    else -> UpdateContextResult.Invalidated
  }
}

private fun applyPrefixAppend(context: InlineCompletionContext, fragment: CharSequence): Pair<List<InlineCompletionElement>, Int>? {
  // only one symbol is permitted
  if (fragment.length != 1 || !context.lineToInsert.startsWith(fragment) || context.lineToInsert == fragment.toString()) {
    return null
  }
  val truncateTyping = fragment.length
  return truncateElementsPrefix(context.state.elements, truncateTyping) to truncateTyping
}

private fun truncateElementsPrefix(elements: List<InlineCompletionElement>, length: Int): List<InlineCompletionElement> {
  var currentLength = length
  val newFirstElementIndex = elements.indexOfFirst {
    currentLength -= it.text.length
    currentLength < 0 // Searching for the element that exceeds [length]
  }
  assert(newFirstElementIndex >= 0)
  currentLength += elements[newFirstElementIndex].text.length
  val newFirstElement = InlineCompletionElement(elements[newFirstElementIndex].text.drop(currentLength))
  return listOf(newFirstElement) + elements.drop(newFirstElementIndex + 1)
}