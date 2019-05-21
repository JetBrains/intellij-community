// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.InlayPresentation

interface InlayHintsSink {
  /**
   * Adds inlay to underlying editor.
   * Note, that only one presentation with the given key may be at the same offset.
   */
  fun addInlineElement(offset: Int, relatesToPrecedingText: Boolean, presentation: InlayPresentation)

  fun addBlockElement(offset: Int, relatesToPrecedingText: Boolean, showAbove: Boolean, priority: Int, presentation: InlayPresentation)
}