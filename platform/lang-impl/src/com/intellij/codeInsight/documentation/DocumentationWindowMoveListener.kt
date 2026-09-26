// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation

import com.intellij.ui.WindowMoveListener
import java.awt.event.MouseEvent
import java.awt.geom.Point2D
import javax.swing.text.BadLocationException
import javax.swing.text.StyledDocument

internal class DocumentationWindowMoveListener(
  private val pane: DocumentationHintEditorPane,
) : WindowMoveListener(pane) {
  override fun mousePressed(event: MouseEvent) {
    val document = pane.document as StyledDocument
    val x = event.x
    val y = event.y
    if (
      hasTextAt(document, x, y) ||
      hasTextAt(document, x + 3, y) ||
      hasTextAt(document, x - 3, y) ||
      hasTextAt(document, x, y + 3) ||
      hasTextAt(document, x, y - 3)
    ) {
      return
    }
    super.mousePressed(event)
  }

  private fun hasTextAt(document: StyledDocument, x: Int, y: Int): Boolean {
    val element = document.getCharacterElement(pane.viewToModel2D(Point2D.Float(x.toFloat(), y.toFloat())))
    try {
      val text = document.getText(element.startOffset, element.endOffset - element.startOffset)
      return !text.trim().isEmpty()
    }
    catch (_: BadLocationException) {
      return false
    }
  }
}
