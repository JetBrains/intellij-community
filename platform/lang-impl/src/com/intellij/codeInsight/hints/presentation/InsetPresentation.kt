// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseEvent

/**
 * Presentation that wraps existing one into rectangle with given insets
 * All mouse events (even those, that are outside of inner rectangle) are still passed to underlying presentation.
 */
class InsetPresentation(
  val presentation: InlayPresentation,
  val left: Int,
  val right: Int,
  val top: Int,
  val down: Int
) : InlayPresentation by presentation {
  override val width: Int
    get() = presentation.width + left + right
  override val height: Int
    get() = presentation.height + top + down

  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    g.withTranslated(left, top) {
      presentation.paint(g, attributes)
    }
  }
}