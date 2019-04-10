// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent

/**
 * Presentation, that delegates to [delegate], which can be dynamically changed.
 */
class DynamicPresentation(delegate: InlayPresentation) : BasePresentation() {
  var delegate: InlayPresentation = delegate
    set(value) {
      if (value != field) {
        field = value
        val previousWidth = field.width
        if (value.width == previousWidth) {
          fireContentChanged(Rectangle(0, 0, width, 0)) // TODO
        } else {
          fireSizeChanged(Dimension(previousWidth, 0), Dimension(value.width, 0)) // TODO
        }
      }
    }

  init {
    delegate.addListener(Listener())
  }

  override val width: Int
    get() = delegate.width
  override val height: Int
    get() = delegate.height

  override fun paint(g: Graphics2D, attributes: TextAttributes) = delegate.paint(g, attributes)

  override fun mouseClicked(e: MouseEvent, editorPoint: Point) = delegate.mouseClicked(e, editorPoint)

  override fun mouseMoved(e: MouseEvent) = delegate.mouseMoved(e)

  override fun mouseExited() = delegate.mouseExited()

  override fun toString(): String = "delegate => $delegate"

  private inner class Listener : PresentationListener {
    override fun contentChanged(area: Rectangle) = fireContentChanged(area)

    override fun sizeChanged(previous: Dimension, current: Dimension) = fireSizeChanged(previous, current)
  }
}