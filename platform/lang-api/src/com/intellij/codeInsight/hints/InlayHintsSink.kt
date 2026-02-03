// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.RootInlayPresentation
import org.jetbrains.annotations.ApiStatus

interface InlayHintsSink {
  /**
   * Adds inline element to underlying editor.
   * @see [com.intellij.openapi.editor.InlayModel.addInlineElement]
   * @param placeAtTheEndOfLine being placed at the end of a line hint doesn't allow to place a caret behind it
   */
  fun addInlineElement(offset: Int, relatesToPrecedingText: Boolean, presentation: InlayPresentation, placeAtTheEndOfLine: Boolean)

  // Left for binary compatibility
  @Deprecated("Use addInlineElement(Int, Boolean, InlayPresentation, Boolean) instead",
              ReplaceWith("addInlineElement(offset, relatesToPrecedingText, presentation, false)"))
  fun addInlineElement(offset: Int, relatesToPrecedingText: Boolean, presentation: InlayPresentation) {
    addInlineElement(offset, relatesToPrecedingText, presentation, false)
  }

  /**
   * Adds block element to underlying editor.
   * Offset doesn't affects position of the inlay in the line, it will be drawn in the very beginning of the line.
   * Presentation must shift itself (see com.intellij.openapi.editor.ex.util.EditorUtil#getPlainSpaceWidth)
   * @see [com.intellij.openapi.editor.InlayModel.addBlockElement]
   */
  fun addBlockElement(offset: Int, relatesToPrecedingText: Boolean, showAbove: Boolean, priority: Int, presentation: InlayPresentation)

  /**
   * API can be changed in 2020.2!
   */
  @ApiStatus.Experimental
  fun addInlineElement(offset: Int, presentation: RootInlayPresentation<*>, constraints: HorizontalConstraints?)

  /**
   * API can be changed in 2020.2!
   */
  @ApiStatus.Experimental
  fun addBlockElement(logicalLine: Int, showAbove: Boolean, presentation: RootInlayPresentation<*>, constraints: BlockConstraints?)
}