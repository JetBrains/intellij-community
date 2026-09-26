// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization

import com.intellij.notebooks.ui.visualization.NotebookEditorAppearance.Companion.NOTEBOOK_APPEARANCE_KEY
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Key
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBScrollPane
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

object NotebookUtil {
  private val IS_JUPYTER_CONSOLE_EDITOR_KEY: Key<Boolean> = Key.create("JUPYTER_HISTORY_EDITOR_KEY")

  var Editor.isJupyterConsoleEditor: Boolean
    get() = getUserData(IS_JUPYTER_CONSOLE_EDITOR_KEY) == true
    set(value) {
      putUserData(IS_JUPYTER_CONSOLE_EDITOR_KEY, value)
    }

  val Editor.notebookAppearance: NotebookEditorAppearance
    get() = NOTEBOOK_APPEARANCE_KEY.get(this)!!

  /** If customColor == null, the gutter will get editor.colorsScheme.editorBackgroundColor */
  fun paintNotebookCellBackgroundGutter(
    editor: EditorImpl,
    g: Graphics,
    r: Rectangle,
    top: Int,
    height: Int,
    presentationModeMasking: Boolean = false,  // PY-74597
    customColor: Color? = null,
    actionBetweenBackgroundAndStripe: () -> Unit = {},
  ) {
    val diffViewOffset = 6  // randomly picked a number that fits well
    val appearance = editor.notebookAppearance
    val borderWidth = appearance.getLeftBorderWidth()
    val gutterWidth = editor.gutterComponentEx.width

    val (fillX, fillWidth, fillColor) = if (!presentationModeMasking) {
      Triple(r.width - borderWidth, borderWidth, customColor ?: appearance.codeCellBackgroundColor())
    }
    else {
      Triple(r.width - borderWidth - gutterWidth, gutterWidth, editor.notebookAppearance.editorBackgroundColor())
    }
    g.color = fillColor

    when (editor.editorKind == EditorKind.DIFF) {
      true -> g.fillRect(fillX + diffViewOffset, top, fillWidth - diffViewOffset, height)
      else -> g.fillRect(fillX, top, fillWidth, height)
    }

    actionBetweenBackgroundAndStripe()
  }

  fun Editor.isOrdinaryNotebookEditor(): Boolean = when {
    this.editorKind != EditorKind.MAIN_EDITOR -> false
    isJupyterConsoleEditor -> false
    else -> true
  }

  fun Editor.isDiffKind(): Boolean = editorKind.isDiff()

  fun EditorKind.isDiff(): Boolean = this === EditorKind.DIFF

  fun getJupyterCellSpacing(editor: Editor): Int = editor.getLineHeight()

  private fun EditorEx.visibleViewportWidthExcludingOverlappingVerticalScrollbar(): Int {
    val viewportWidth = scrollPane.viewport?.width ?: scrollingModel.visibleArea.width
    return (viewportWidth - overlappingVerticalScrollbarWidth()).coerceAtLeast(0)
  }

  fun EditorEx.visibleNotebookCellWidth(): Int {
    val width = visibleViewportWidthExcludingOverlappingVerticalScrollbar()
    val reservedWidth = rightReservedViewportStripWidth()
    val inset = if (reservedWidth > notebookAppearance.cellBorderHeight) {
      minOf(notebookAppearance.cellBorderHeight / 4, reservedWidth)
    }
    else {
      0
    }
    return (width - inset).coerceAtLeast(0)
  }

  fun EditorEx.overlappingVerticalScrollbarLeftShift(): Int {
    if (!isVerticalScrollbarOnLeft(scrollPane)) return 0
    return overlappingVerticalScrollbarWidth()
  }

  private fun EditorEx.overlappingVerticalScrollbarWidth(): Int {
    val viewport = scrollPane.viewport ?: return 0
    val scrollBar = scrollPane.verticalScrollBar ?: return 0
    if (!scrollBar.isVisible || !scrollBar.isEnabled || scrollBar.width <= 0) return 0

    val viewportBounds = viewport.bounds
    val scrollBarBounds = boundsInScrollPane(scrollPane, scrollBar)
    val horizontalOverlap = minOf(viewportBounds.x + viewportBounds.width, scrollBarBounds.x + scrollBarBounds.width) -
                            maxOf(viewportBounds.x, scrollBarBounds.x)
    val verticalOverlap = minOf(viewportBounds.y + viewportBounds.height, scrollBarBounds.y + scrollBarBounds.height) -
                          maxOf(viewportBounds.y, scrollBarBounds.y)
    if (horizontalOverlap <= 0 || verticalOverlap <= 0) return 0

    return horizontalOverlap.coerceAtMost(scrollBar.width)
  }

  private fun EditorEx.rightReservedViewportStripWidth(): Int {
    val viewport = scrollPane.viewport ?: return 0
    val scrollBar = scrollPane.verticalScrollBar ?: return 0
    if (isVerticalScrollbarOnLeft(scrollPane) || !scrollBar.isVisible || scrollBar.width <= 0) return 0

    val viewportRight = viewport.bounds.x + viewport.bounds.width
    val scrollBarBounds = boundsInScrollPane(scrollPane, scrollBar)
    return (scrollBarBounds.x - viewportRight).coerceAtLeast(0)
  }

  private fun boundsInScrollPane(scrollPane: JScrollPane, component: Component): Rectangle {
    val parent = component.parent ?: return component.bounds
    return SwingUtilities.convertRectangle(parent, component.bounds, scrollPane)
  }

  private fun isVerticalScrollbarOnLeft(scrollPane: JScrollPane): Boolean {
    val flip = scrollPane.getClientProperty(JBScrollPane.Flip::class.java) as? JBScrollPane.Flip ?: JBScrollPane.Flip.NONE
    return if (scrollPane.componentOrientation.isLeftToRight) {
      flip === JBScrollPane.Flip.HORIZONTAL || flip === JBScrollPane.Flip.BOTH
    }
    else {
      flip === JBScrollPane.Flip.NONE || flip === JBScrollPane.Flip.VERTICAL
    }
  }
}

fun isWebOutputsDarkTheme(backgroundColor: Color): Boolean = ColorUtil.isDark(backgroundColor)
