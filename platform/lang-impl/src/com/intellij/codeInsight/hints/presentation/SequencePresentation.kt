// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent

class SequencePresentation(private val presentations: List<InlayPresentation>) : BasePresentation() {
  init {
    assert(presentations.isNotEmpty())
    for (presentation in presentations) {
      presentation.addListener(object: PresentationListener {
        override fun contentChanged(area: Rectangle) {
          // TODO incorrect area!!!
          this@SequencePresentation.fireContentChanged(area)
        }

        override fun sizeChanged(previous: Dimension, current: Dimension) {
          startOffsets = calculateOffsets()
          // TODO incorrect area!!!
          this@SequencePresentation.fireSizeChanged(previous, current)
        }
      })
    }
  }

  private var presentationUnderCursor: InlayPresentation? = null

  var startOffsets: IntArray = calculateOffsets()

  private fun calculateOffsets(): IntArray {
    var currentOffset = 0
    return IntArray(presentations.size) { width ->
      val oldOffset = currentOffset
      currentOffset = oldOffset + width
      oldOffset
    }
  }

  override val width: Int = startOffsets.last()
  override val height: Int = presentations.maxBy { it.height }!!.height

  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    var xOffset = 0
    try {
      for (presentation in presentations) {
        presentation.paint(g, attributes)
        xOffset += presentation.width
        g.translate(presentation.width, 0)
      }
    } finally {
      g.translate(-xOffset, 0)
    }
  }

  /**
   * Note: height is not considered
   */
  override fun mouseClicked(e: MouseEvent, editorPoint: Point) {
    val x = e.x
    val index = startOffsets.binarySearch(x) + 1
    val presentation = presentations[index]
    val offset = startOffsets[index]
    e.withTranslated(offset, 0) {
      presentation.mouseClicked(e, editorPoint)
    }
  }

  /**
   * Note: height is not considered
   */
  override fun mouseMoved(e: MouseEvent) {
    // Height is not considered
    val x = e.x
    val index = startOffsets.binarySearch(x) + 1
    val presentation = presentations[index]
    if (presentationUnderCursor != presentation) {
      if (presentationUnderCursor != null) {
        presentationUnderCursor?.mouseExited()
      }
      presentationUnderCursor = presentation
    }
    val xOffset = startOffsets[index]
    e.withTranslated(xOffset, 0) {
      presentation.mouseMoved(e)
    }
  }

  override fun mouseExited() {
    presentationUnderCursor?.mouseExited()
    presentationUnderCursor = null
  }

  override fun toString(): String = presentations.joinToString(" ") { "[$it]" }
}