// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.presentation

import java.awt.Color

class PillWithBackgroundPresentation(
  presentation: InlayPresentation,
  color: Color? = null,
  backgroundAlpha : Float = 0.55f
): AbstractRoundWithBackgroundPresentation(presentation, color, backgroundAlpha) {
  override val arcWidth: Int
    get() = presentation.height
  override val arcHeight: Int
    get() = presentation.height
}