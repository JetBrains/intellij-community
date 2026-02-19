// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.OverrideOnly
import java.awt.Rectangle

/**
 * Default cases to use:
 * - [InlineCompletionGrayTextElement] default gray text element to render
 * - [InlineCompletionTextElement] text element with custom attributes to render
 * - [InlineCompletionSkipTextElement] allows to jump over already existing text in an editor
 */
@ApiStatus.NonExtendable
interface InlineCompletionElement {

  /**
   * Text to insert for current element
   */
  val text: String

  fun toPresentable(): Presentable

  /**
   * `Presentable` is a `Disposable` interface that provides additional methods for rendering an inline element.
   */
  interface Presentable : Disposable {
    val element: InlineCompletionElement

    /**
     * Checks if the presentable `InlineCompletionElement` is visible.
     *
     * @return `true` if the element is visible, otherwise `false`.
     */
    fun isVisible(): Boolean

    /**
     * Renders the presentable `InlineCompletionElement` at the specified offset in the editor.
     *
     * @param editor The editor where the element is rendered.
     * @param offset The offset in the document where the element is rendered.
     */
    fun render(editor: Editor, offset: Int)

    /**
     * Gets the bounds of the inline element as a `Rectangle`.
     *
     * @return a `Rectangle` representing the bounds of the inline element, or `null` if element is not rendered yet.
     */
    @OverrideOnly
    fun getBounds(): Rectangle?

    /**
     * Gets the start offset of the inline element.
     *
     * @return the start offset of the inline element, or `null` if element is not rendered yet.
     */
    fun startOffset(): Int?

    /**
     * Gets the end offset of the inline element.
     *
     * @return the end offset of the inline element, or `null` if element is not rendered yet.
     */
    fun endOffset(): Int?
  }
}