// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

class EditorCodePreview(val editor: Editor): Disposable {

  val tracker = LocationTracker(editor.component)

  var popups: List<CodeFragmentPopup> = emptyList()

  fun addPreview(lines: IntRange, onClick: () -> Unit){
    val newPopup = CodeFragmentPopup(editor, lines, onClick)
    Disposer.register(this, newPopup)
    popups = (popups + newPopup).sortedBy { it.lines.first }
    updatePopupPositions()
  }

  val documentListener = object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      popups.forEach(CodeFragmentPopup::updateCodePreview)
      updatePopupPositions()
    }
  }

  override fun dispose() {
  }

  init {
    tracker.subscribe { updatePopupPositions() }
    Disposer.register(this, tracker)
    editor.scrollingModel.addVisibleAreaListener(VisibleAreaListener { updatePopupPositions() }, this)
    editor.document.addDocumentListener(documentListener, this)
  }

  fun updatePopupPositions() {
    var visibleArea = editor.scrollingModel.visibleArea
    var positions = popups.reversed()
      .map { popup ->
        val linesArea = findLinesArea(editor, popup.lines)
        val position = visibleArea.relativePositionOf(linesArea)
        visibleArea = visibleArea.subtracted(linesArea, position)
        position
      }
      .reversed()
    if (visibleArea.height < 0) positions = popups.map { RelativePosition.Inside }
    val popupsAndPositions = popups.zip(positions)

    popupsAndPositions
      .filter { (_, position) -> position == RelativePosition.Inside }
      .forEach { (popup, _) ->
        popup.hide()
      }

    var position = Point(-3, -3)
    popupsAndPositions.filter { (_, position) -> position == RelativePosition.Top }
      .forEach { (popup, _) ->
        popup.window.location = RelativePoint(editor.component, position).screenPoint
        position.translate(0, popup.window.height)
      }
    position = Point(-3, editor.scrollingModel.visibleArea.height)
    popupsAndPositions
      .filter { (_, position) -> position == RelativePosition.Bottom }
      .reversed()
      .forEach { (popup, _) ->
        position.translate(0, -popup.window.height)
        popup.window.location = RelativePoint(editor.component, position).screenPoint
      }
    popupsAndPositions
      .filterNot { (_, position) -> position == RelativePosition.Inside }
      .forEach { (popup, _) ->
        popup.show()
        popup.window.size = Dimension(editor.component.width, popup.window.preferredSize.height)
        popup.window.validate()
      }
  }

  private fun Rectangle.subtracted(area: Rectangle, position: RelativePosition): Rectangle {
    return when (position) {
      RelativePosition.Top -> Rectangle(x, y + area.height, width, height - area.height)
      RelativePosition.Bottom -> Rectangle(x, y, width, height - area.height)
      else -> this
    }
  }

  private fun Rectangle.relativePositionOf(area: Rectangle): RelativePosition {
    return when {
      area.y < y -> RelativePosition.Top
      area.y + area.height > y + height -> RelativePosition.Bottom
      else -> RelativePosition.Inside
    }
  }

  private enum class RelativePosition {
    Top, Bottom, Inside
  }

  private fun findLinesArea(editor: Editor, lines: IntRange): Rectangle {
    val visibleArea = editor.scrollingModel.visibleArea
    val y = editor.visualLineToY(lines.first)
    val height = lines.length * editor.lineHeight
    return Rectangle(visibleArea.x, y, visibleArea.width, height)
  }

  fun findEditorLinesOf(rectangle: Rectangle): IntRange {
    val start = editor.yToVisualLine(rectangle.y)
    val end = editor.yToVisualLine(rectangle.y + rectangle.height)
    return start..end
  }

}