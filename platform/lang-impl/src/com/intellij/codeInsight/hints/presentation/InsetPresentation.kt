// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseEvent

/**
 * Presentation that wraps existing one into rectangle with given insets
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

  override fun mouseClicked(e: MouseEvent, editorPoint: Point) {
    if (isInBounds(e)) {
      e.withTranslated(left, top) {
        presentation.mouseClicked(e, editorPoint)
      }
    }
  }

  private fun isInBounds(e: MouseEvent): Boolean {
    val eventX = e.x
    val eventY = e.y
    return eventX > left && eventX < presentation.width - (left + right) && eventY > top && eventY < presentation.height - (top + down)
  }

  override fun mouseMoved(e: MouseEvent) {
    if (isInBounds(e)) {
      e.withTranslated(left, top) {
        presentation.mouseMoved(e)
      }
    }
  }

  override fun mouseExited() {
    super.mouseExited()
    // TODO
  }
}