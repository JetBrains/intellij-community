// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.InlayPresentation

interface InlayHintsSink {
  /**
   * Adds inline element to underlying editor.
   * Note, that single provider may add only one presentation to the given offset. This requirement may be relaxed in future.
   * @see [com.intellij.openapi.editor.InlayModel.addInlineElement]
   */
  fun addInlineElement(offset: Int, relatesToPrecedingText: Boolean, presentation: InlayPresentation)

  /**
   * Adds block element to underlying editor.
   * Offset doesn't affects position of the inlay in the line, it will be drawn in the very beginning of the line.
   * Presentation must shift itself (see com.intellij.openapi.editor.ex.util.EditorUtil#getPlainSpaceWidth)
   * Note, that single provider may add only one presentation to the given offset. This requirement may be relaxed in future.
   * @see [com.intellij.openapi.editor.InlayModel.addBlockElement]
   */
  fun addBlockElement(offset: Int, relatesToPrecedingText: Boolean, showAbove: Boolean, priority: Int, presentation: InlayPresentation)
}