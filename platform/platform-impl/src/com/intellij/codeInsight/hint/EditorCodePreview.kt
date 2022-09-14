// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.PositionTracker
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent

/**
 * Ensures that in-editor code fragments are always visible.
 * For any invisible fragment creates a popup and places it on top of the first or last visible lines (depends on required scroll direction).
 */
interface EditorCodePreview: Disposable {

  fun addPreview(lines: IntRange, onClickAction: () -> Unit)

  companion object {

    private val EDITOR_PREVIEW_KEY = Key<EditorCodePreview>("EditorCodePreview")

    fun create(editor: Editor): EditorCodePreview {
      require(getActivePreview(editor) == null)
      val codePreview = if (ApplicationManager.getApplication().isUnitTestMode) EditorCodePreviewTestImpl() else EditorCodePreviewImpl(editor)
      EditorUtil.disposeWithEditor(editor, codePreview)
      editor.putUserData(EDITOR_PREVIEW_KEY, codePreview)
      Disposer.register(codePreview, Disposable { editor.putUserData(EDITOR_PREVIEW_KEY, null) })
      return codePreview
    }

    private fun getActivePreview(editor: Editor): EditorCodePreview? {
      return editor.getUserData(EDITOR_PREVIEW_KEY)
    }
  }
}

private class EditorCodePreviewImpl(val editor: Editor): EditorCodePreview, Disposable {

  private var popups: List<CodeFragmentPopup> = emptyList()

  private val documentListener = object : BulkAwareDocumentListener.Simple {
    override fun afterDocumentChange(document: Document) {
      if (editor.isDisposed) return
      popups.forEach(CodeFragmentPopup::updateCodePreview)
      updatePopupPositions()
    }
  }

  var editorVisibleArea: Rectangle = editor.scrollingModel.visibleArea
  private set

  init {
    addPositionListener(this, editor.component) { updatePopupPositions() }
    editor.scrollingModel.addVisibleAreaListener(VisibleAreaListener { updatePopupPositions() }, this)
    editor.document.addDocumentListener(documentListener, this)
  }

  override fun addPreview(lines: IntRange, onClickAction: () -> Unit){
    val newPopup = CodeFragmentPopup(editor, lines, onClickAction)
    Disposer.register(this, newPopup)
    popups = (popups + newPopup).sortedBy { it.lines.first }
    updatePopupPositions()
  }

  override fun dispose() { }

  private fun updatePopupPositions() {
    if (editor.isDisposed) return
    var visibleArea = editor.scrollingModel.visibleArea
    var positions = popups.reversed()
      .map { popup ->
        val linesArea = findLinesArea(editor, popup.lines)
        val position = visibleArea.relativePositionOf(linesArea)
        visibleArea = visibleArea.subtracted(linesArea, position)
        position
      }
      .reversed()
    if (visibleArea.height < 0) {
      positions = popups.map { RelativePosition.Inside }
      editorVisibleArea = editor.scrollingModel.visibleArea
    } else {
      editorVisibleArea = visibleArea
    }
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
    val y = editor.logicalPositionToXY(LogicalPosition(lines.first, 0)).y
    val lineNumber = lines.last - lines.first + 1
    val height = lineNumber * editor.lineHeight
    return Rectangle(visibleArea.x, y, visibleArea.width, height)
  }

  private fun addPositionListener(disposable: Disposable, component: JComponent, listener: () -> Unit) {
    val tracker = object : PositionTracker<Component>(component) {
      override fun recalculateLocation(visibleArea: Component): RelativePoint = RelativePoint(component, Point(0, 0))
    }
    Disposer.register(disposable, tracker)
    val client = object : PositionTracker.Client<Component> {
      override fun dispose() {}
      override fun revalidate() {
        revalidate(tracker)
      }
      override fun revalidate(tracker: PositionTracker<Component>) {
        listener()
      }
    }
    Disposer.register(disposable, client)
    tracker.init(client)
  }

}

private class EditorCodePreviewTestImpl: EditorCodePreview {
  override fun addPreview(lines: IntRange, onClickAction: () -> Unit) {}
  override fun dispose() {}
}