// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElementManipulator.Companion.getApplicable
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to customize behaviour of manipulating over [InlineCompletionElement].
 *
 * * If you want to support symbols over typing while a user types, implement [truncateFirstSymbol].
 * * ... to be continued.
 *
 * @see getApplicable
 * @see InlineCompletionSuggestionUpdateManager
 */
@ApiStatus.Experimental
interface InlineCompletionElementManipulator {

  /**
   * Whether this manipulator supports such an [element]. It is guaranteed that other methods are called, only if [isApplicable] is `true`.
   */
  fun isApplicable(element: InlineCompletionElement): Boolean

  /**
   * Returns a new instance of [InlineCompletionElement] that has the same content as [element] but with its first symbol truncated.
   * If [element] is emptied after truncation, the method should return `null`.
   *
   * It is guaranteed that [element] has at least one symbol before truncation.
   *
   * This method is called only if [isApplicable] returns `true` for [element].
   *
   * @see InlineCompletionSuggestionUpdateManager
   */
  fun truncateFirstSymbol(element: InlineCompletionElement): InlineCompletionElement? {
    return substring(element, 1, element.text.length)
  }

  @ApiStatus.Experimental
  fun substring(element: InlineCompletionElement, startOffset: Int, endOffset: Int): InlineCompletionElement? = null

  companion object {
    private val EP_NAME = ExtensionPointName.create<InlineCompletionElementManipulator>(
      "com.intellij.inline.completion.element.manipulator"
    )

    /**
     * Returns the first [InlineCompletionElementManipulator] that [isApplicable] for [element].
     *
     * If you want to override [InlineCompletionElementManipulator] for some kind of elements,
     * implement this class and set `order=first` in your extension point implementation.
     */
    fun getApplicable(element: InlineCompletionElement): InlineCompletionElementManipulator? {
      return EP_NAME.extensionList.firstOrNull { it.isApplicable(element) }
    }
  }
}

@ApiStatus.Internal
class InlineCompletionGrayTextElementManipulator : InlineCompletionElementManipulator {
  override fun isApplicable(element: InlineCompletionElement): Boolean {
    return element is InlineCompletionGrayTextElement
  }

  override fun substring(element: InlineCompletionElement, startOffset: Int, endOffset: Int): InlineCompletionElement? {
    element as InlineCompletionGrayTextElement
    if (startOffset >= endOffset) {
      return null
    }
    return InlineCompletionGrayTextElement(element.text.substring(startOffset, endOffset))
  }
}

@ApiStatus.Internal
class InlineCompletionColorTextElementManipulator : InlineCompletionElementManipulator {
  override fun isApplicable(element: InlineCompletionElement): Boolean {
    return element is InlineCompletionColorTextElement && element !is InlineCompletionGrayTextElement
  }

  override fun substring(element: InlineCompletionElement, startOffset: Int, endOffset: Int): InlineCompletionElement? {
    element as InlineCompletionColorTextElement
    if (startOffset >= endOffset) {
      return null
    }
    return InlineCompletionColorTextElement(element.text.substring(startOffset, endOffset), element.getColor)
  }
}

@ApiStatus.Internal
class InlineCompletionTextElementManipulator : InlineCompletionElementManipulator {
  override fun isApplicable(element: InlineCompletionElement): Boolean {
    return element is InlineCompletionTextElement && element !is InlineCompletionColorTextElement
  }

  override fun substring(element: InlineCompletionElement, startOffset: Int, endOffset: Int): InlineCompletionElement? {
    element as InlineCompletionTextElement
    if (startOffset >= endOffset) {
      return null
    }
    return InlineCompletionTextElement(element.text.substring(startOffset, endOffset), element.getAttributes)
  }
}
