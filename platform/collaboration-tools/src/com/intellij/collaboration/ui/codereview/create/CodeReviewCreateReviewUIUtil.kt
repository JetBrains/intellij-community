// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.create

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewMarkdownEditor
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsCommitMetadata
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SpringLayout
import javax.swing.border.AbstractBorder
import javax.swing.text.AttributeSet
import javax.swing.text.PlainDocument

object CodeReviewCreateReviewUIUtil {
  private val titleFont
    get() = JBUI.Fonts.label(16f)

  fun JBTextArea.applyDefaults() {
    font = titleFont
    background = UIUtil.getListBackground()
    lineWrap = true
  }

  fun createTitleEditor(emptyText: String = ""): JBTextArea = JBTextArea(SingleLineDocument()).apply {
    applyDefaults()
    this.emptyText.text = emptyText
    preferredSize = Dimension(0, JBUI.scale(font.size * 5))
  }.also {
    CollaborationToolsUIUtil.registerFocusActions(it)
  }

  fun createTitleEditor(project: Project, emptyText: @Nls String = ""): Editor =
    CodeReviewMarkdownEditor.create(project, true, true).apply {
      component.font = titleFont

      if (this !is EditorEx) return@apply
      configurePlaceholder(emptyText)
      setScrollbarsBackground()
    }

  fun createDescriptionEditor(project: Project, emptyText: @Nls String = ""): Editor =
    CodeReviewMarkdownEditor.create(project, true, false).apply {
      if (this !is EditorEx) return@apply
      configurePlaceholder(emptyText)
      setShowPlaceholderWhenFocused(true)
      setScrollbarsBackground()
    }

  private fun EditorEx.configurePlaceholder(emptyText: @Nls String) {
    setPlaceholder(emptyText)
    setShowPlaceholderWhenFocused(true)
  }

  private fun EditorEx.setScrollbarsBackground() {
    val editorBackground = JBColor.lazy { EditorColorsManager.getInstance().globalScheme.defaultBackground }
    scrollPane.horizontalScrollBar?.background = editorBackground
    scrollPane.verticalScrollBar?.background = editorBackground
  }

  fun createCommitListCellRenderer(): ListCellRenderer<VcsCommitMetadata> = CodeReviewTwoLinesCommitRenderer()

  @ApiStatus.Internal
  fun createGenerationToolbarOverlay(editorPanel: JComponent, toolbar: ActionToolbar, getSpacerWidth: (() -> Int)? = null): JComponent {
    val buttonPanel = toolbar.component.apply {
      border = JBUI.Borders.empty()
      isOpaque = false
      putClientProperty(ActionToolbarImpl.IMPORTANT_TOOLBAR_KEY, true)
    }

    return JBLayeredPane().apply {
      val layout = SpringLayout()
      this.layout = layout

      layout.putConstraint(SpringLayout.NORTH, editorPanel, 0, SpringLayout.NORTH, this)
      layout.putConstraint(SpringLayout.SOUTH, editorPanel, 0, SpringLayout.SOUTH, this)
      layout.putConstraint(SpringLayout.EAST, editorPanel, 0, SpringLayout.EAST, this)
      layout.putConstraint(SpringLayout.WEST, editorPanel, 0, SpringLayout.WEST, this)
      add(editorPanel, 1 as Any)

      layout.putConstraint(SpringLayout.SOUTH, buttonPanel, 0, SpringLayout.SOUTH, this)

      if (getSpacerWidth != null) {
        // Needed to avoid overlapping with the editor's vertical scrollbar
        val spacer = JPanel().apply {
          isOpaque = false
          isEnabled = false
          border = object : AbstractBorder() {
            override fun getBorderInsets(c: Component?, insets: Insets): Insets {
              super.getBorderInsets(c, insets)
              insets.right = getSpacerWidth()
              return insets
            }
          }
        }
        layout.putConstraint(SpringLayout.SOUTH, spacer, 0, SpringLayout.SOUTH, this)
        layout.putConstraint(SpringLayout.EAST, spacer, 0, SpringLayout.EAST, this)
        add(spacer, 0 as Any)

        layout.putConstraint(SpringLayout.EAST, buttonPanel, 0, SpringLayout.WEST, spacer)
      }
      else {
        layout.putConstraint(SpringLayout.EAST, buttonPanel, 0, SpringLayout.EAST, this)
      }

      add(buttonPanel, 2 as Any)
    }
  }
}

@ApiStatus.Internal
open class SingleLineDocument : PlainDocument() {
  override fun insertString(offs: Int, str: String, a: AttributeSet?) {
    // filter new lines
    val withoutNewLines = StringUtil.replace(str, "\n", "")
    super.insertString(offs, withoutNewLines, a)
  }
}
