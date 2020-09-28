// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.awt.RelativePoint
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

class EditorCodePreview(val editor: Editor, lines: List<IntRange>): Disposable {

  companion object {
    private val EDITOR_CODE_PREVIEW = Key<EditorCodePreview>("InplaceMethodExtractor")

    fun getActivePreview(editor: Editor): EditorCodePreview? {
      return editor.getUserData(EDITOR_CODE_PREVIEW)
    }

    fun setActivePreview(editor: Editor, preview: EditorCodePreview?) {
      editor.putUserData(EDITOR_CODE_PREVIEW, preview)
    }
  }

  val tracker = LocationTracker(editor.component)

  val popups = lines.map { lineRange -> CodeFragmentPopup(editor, lineRange) }.sortedBy { it.lines.first }

  val documentListener = object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      popups.forEach(CodeFragmentPopup::updateCodePreview)
    }
  }

  override fun dispose() {
    if (updateOnDocumentChange) editor.document.removeDocumentListener(documentListener)
    setActivePreview(editor, null)
  }

  var updateOnDocumentChange: Boolean = false
    set(value) {
      if (field && ! value) {
        editor.document.removeDocumentListener(documentListener)
      }
      if (value && ! field) {
        editor.document.addDocumentListener(documentListener)
      }
      field = value
    }

  init {
    updatePopupPositions()
    tracker.subscribe {
      updatePopupPositions()
    }
    editor.scrollingModel.addVisibleAreaListener(VisibleAreaListener { updatePopupPositions() }, this)
    popups.forEach { popup -> Disposer.register(this, popup) }
    Disposer.register(this, tracker)
  }

  fun updatePopupPositions() {
    var visibleArea = editor.scrollingModel.visibleArea
    var positions = popups.map { popup ->
      val linesArea = findLinesArea(editor, popup.lines)
      when {
        linesArea.y < visibleArea.y -> {
          visibleArea = Rectangle(visibleArea.x, visibleArea.y + linesArea.height, visibleArea.width, visibleArea.height - linesArea.height)
          PopupPosition.Top
        }
        linesArea.y + linesArea.height > visibleArea.y + visibleArea.height -> {
          visibleArea = Rectangle(visibleArea.x, visibleArea.y, visibleArea.width, visibleArea.height - linesArea.height)
          PopupPosition.Bottom
        }
        else -> {
          PopupPosition.Hidden
        }
      }
    }

    if (visibleArea.height < 0) positions = popups.map { PopupPosition.Hidden }

    popups.zip(positions)
      .filter { (_, position) -> position == PopupPosition.Hidden }
      .forEach { (popup, _) ->
        popup.hide()
      }
    var position = Point(-3, -3)
    popups.zip(positions)
      .filter { (_, position) -> position == PopupPosition.Top }
      .forEach { (popup, _) ->
        popup.window.location = RelativePoint(editor.component, position).screenPoint
        position.translate(0, popup.window.height)
      }
    position = Point(-3, editor.scrollingModel.visibleArea.height)
    popups.zip(positions).reversed()
      .filter { (_, position) -> position == PopupPosition.Bottom }
      .forEach { (popup, _) ->
        position.translate(0, -popup.window.height)
        popup.window.location = RelativePoint(editor.component, position).screenPoint
      }
    popups.zip(positions)
      .filterNot { (_, position) -> position == PopupPosition.Hidden }
      .forEach { (popup, _) ->
        popup.show()
      }
  }

  enum class PopupPosition {
    Top, Bottom, Hidden
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