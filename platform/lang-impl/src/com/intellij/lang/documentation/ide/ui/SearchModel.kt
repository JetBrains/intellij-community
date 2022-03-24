// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DuplicatedCode") // extracted from org.jetbrains.r.rendering.toolwindow.RDocumentationComponent

package com.intellij.lang.documentation.ide.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.LightColors
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.addPropertyChangeListener
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import kotlin.math.abs

internal class SearchModel(ui: DocumentationUI) : Disposable {

  private val editorPane: JEditorPane = ui.editorPane

  val searchField = SearchTextField()

  val matchLabel: JLabel = JLabel().also { label ->
    // adapted from com.intellij.find.editorHeaderActions.StatusTextAction
    label.font = JBUI.Fonts.toolbarFont()
    label.text = "9888 results" //NON-NLS
    label.preferredSize = label.preferredSize
    label.text = null
    label.horizontalAlignment = SwingConstants.RIGHT
  }

  init {
    searchField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        val pattern = searchField.text
        setPattern(pattern)
        near()
      }
    })
    searchField.addKeyboardListener(object : KeyAdapter() {
      override fun keyReleased(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ENTER && hasNext) {
          next()
        }
      }
    })
    Disposer.register(this, ui.addContentListener {
      updateIndices()
      updateHighlighting()
    })
    editorPane.addPropertyChangeListener(parent = this, "highlighter") {
      updateHighlighting()
    }
  }

  private var pattern: String = ""
  private val indices = ArrayList<Int>()
  private var currentSelection = 0
  private val tagHandles = ArrayList<() -> Unit>()

  override fun dispose() {
    pattern = ""
    indices.clear()
    currentSelection = -1
    removeHighlights()
  }

  fun createNavigationActions(): List<AnAction> = listOf(
    object : DumbAwareAction() {
      init {
        ActionUtil.copyFrom(this, IdeActions.ACTION_PREVIOUS_OCCURENCE)
      }

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = hasPrev
      }

      override fun actionPerformed(e: AnActionEvent) = prev()
    },
    object : DumbAwareAction() {
      init {
        ActionUtil.copyFrom(this, IdeActions.ACTION_NEXT_OCCURENCE)
      }

      override fun actionPerformed(e: AnActionEvent) = next()

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = hasNext
      }
    },
  )

  private fun setPattern(pattern: String) {
    if (this.pattern == pattern) return
    this.pattern = pattern
    updateIndices()
    updateHighlighting()
  }

  private val hasNext: Boolean
    get() = currentSelection + 1 < indices.size

  private val hasPrev: Boolean
    get() = currentSelection - 1 >= 0

  private fun next() {
    check(hasNext) { "Doesn't have next element" }
    currentSelection += 1
    updateHighlighting()
    updateMatchLabel()
    scroll()
  }

  private fun prev() {
    check(hasPrev) { "Doesn't have prev element" }
    currentSelection -= 1
    updateHighlighting()
    updateMatchLabel()
    scroll()
  }

  private fun near() {
    val visibleRect = editorPane.visibleRect
    val visibleRestCenter = Point(visibleRect.centerX.toInt(), visibleRect.centerY.toInt())
    val currentOffset = editorPane.viewToModel2D(visibleRestCenter)
    val nearestSelection = indices.minByOrNull { abs(it - currentOffset) } ?: return
    currentSelection = indices.indexOf(nearestSelection)
    updateHighlighting()
    updateMatchLabel()
    scroll()
  }

  private fun scroll() {
    val viewRectangle = editorPane.modelToView(indices[currentSelection])
    editorPane.scrollRectToVisible(viewRectangle)
  }

  private fun updateIndices() {
    indices.clear()
    currentSelection = 0
    if (pattern.isNotEmpty()) {
      val text = editorPane.document.getText(0, editorPane.document.length)
      var index = 0
      while (index < text.length) {
        index = StringUtil.indexOfIgnoreCase(text, pattern, index)
        if (index == -1) break
        indices.add(index)
        index += pattern.length
      }
    }
    updateMatchLabel()
  }

  private fun updateMatchLabel() {
    matchLabel.foreground = UIUtil.getLabelForeground()
    matchLabel.font = JBUI.Fonts.toolbarFont()
    val matches = indices.size
    val cursorIndex = currentSelection + 1
    if (pattern.isEmpty()) {
      searchField.textEditor.background = UIUtil.getTextFieldBackground()
      matchLabel.text = ""
    }
    else if (matches > 0) {
      searchField.textEditor.background = UIUtil.getTextFieldBackground()
      matchLabel.text = ApplicationBundle.message("editorsearch.current.cursor.position", cursorIndex, matches)
    }
    else {
      searchField.textEditor.background = LightColors.RED
      matchLabel.foreground = UIUtil.getErrorForeground()
      matchLabel.text = ApplicationBundle.message("editorsearch.matches", matches)
    }
  }

  private fun removeHighlights() {
    for (tagHandle in tagHandles) {
      tagHandle()
    }
    tagHandles.clear()
  }

  private fun updateHighlighting() {
    removeHighlights()
    val highlighter = editorPane.highlighter ?: return
    editorPane.invalidate()
    editorPane.repaint()
    for (index in indices) {
      val tag = highlighter.addHighlight(index, index + pattern.length, SearchHighlighterPainter(indices[currentSelection] == index))
      tagHandles.add {
        highlighter.removeHighlight(tag)
      }
    }
  }
}
