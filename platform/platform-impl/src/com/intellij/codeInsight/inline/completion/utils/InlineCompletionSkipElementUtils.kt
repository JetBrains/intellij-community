// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.utils

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElementManipulator
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.annotations.ApiStatus

/**
 * For each offset in [offsets]:
 * * Searches for the Inline Completion element and inner symbol corresponding to that offset
 * * Splits this element into two parts: strictly before that symbol and strictly after
 * * Removes the element from the resulting list
 *   and inserts the left split part, the new skip element with the symbol and the right split part
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun List<InlineCompletionElement>.insertSkipElementsAt(offsets: List<Int>): List<InlineCompletionElement> = buildList {
  val elements = this@insertSkipElementsAt.toMutableList()
  var offset = 0
  var elementIndex = 0
  for (skipOffset in offsets.distinct().sorted()) {
    while (elementIndex < elements.size && offset + elements[elementIndex].text.length <= skipOffset) {
      add(elements[elementIndex])
      offset += elements[elementIndex].text.length
      elementIndex++
    }
    if (elementIndex >= elements.size) {
      return@buildList
    }
    val element = elements[elementIndex]
    if (element is InlineCompletionSkipTextElement) {
      continue
    }
    val splitOffset = skipOffset - offset
    val manipulator = InlineCompletionElementManipulator.getApplicable(element)!!
    addIfNotNull(manipulator.substring(element, 0, splitOffset))
    add(InlineCompletionSkipTextElement(element.text.substring(splitOffset, splitOffset + 1)))

    val leftPart = manipulator.substring(element, splitOffset + 1, element.text.length)
    if (leftPart != null) {
      elements[elementIndex] = leftPart
    }
    else {
      elementIndex++
    }
    offset = skipOffset + 1
  }
  while (elementIndex < elements.size) {
    add(elements[elementIndex])
    elementIndex++
  }
}
