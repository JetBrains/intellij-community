// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.codeInsight.inline.completion.InlineCompletionOvertyper
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElementManipulator.Companion.getApplicable
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to customize behaviour of manipulating over [InlineCompletionElement].
 *
 * * If you want to support symbols over typing while a user types, implement [truncateFirstSymbol].
 * * ... to be continued.
 *
 * @see getApplicable
 * @see InlineCompletionOvertyper
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
   * @see InlineCompletionOvertyper
   */
  fun truncateFirstSymbol(element: InlineCompletionElement): InlineCompletionElement?

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

class InlineCompletionGrayTextElementManipulator : InlineCompletionElementManipulator {
  override fun isApplicable(element: InlineCompletionElement): Boolean {
    return element is InlineCompletionGrayTextElement
  }

  override fun truncateFirstSymbol(element: InlineCompletionElement): InlineCompletionElement? {
    return if (element.text.length > 1) InlineCompletionGrayTextElement(element.text.drop(1)) else null
  }
}
