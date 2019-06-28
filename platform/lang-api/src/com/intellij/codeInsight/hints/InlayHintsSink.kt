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
   * Note, that single provider may add only one presentation to the given offset. This requirement may be relaxed in future.
   * @see [com.intellij.openapi.editor.InlayModel.addBlockElement]
   */
  fun addBlockElement(offset: Int, relatesToPrecedingText: Boolean, showAbove: Boolean, priority: Int, presentation: InlayPresentation)
}