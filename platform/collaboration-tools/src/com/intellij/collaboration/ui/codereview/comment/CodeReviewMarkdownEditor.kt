// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.zoomIndicator.ZoomIndicatorManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ErrorBorderCapable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.EditorTextField
import com.intellij.util.LocalTimeCounter
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.border.Border

internal object CodeReviewMarkdownEditor {
  fun create(project: Project, inline: Boolean = false, oneLine: Boolean = false): Editor {
    // setup markdown only if plugin is enabled
    val fileType = FileTypeRegistry.getInstance().getFileTypeByExtension("md").takeIf { it != FileTypes.UNKNOWN } ?: FileTypes.PLAIN_TEXT
    val psiFile = PsiFileFactory.getInstance(project)
      .createFileFromText("Dummy.md", fileType, "", LocalTimeCounter.currentTime(), true, false)

    val editorFactory = EditorFactory.getInstance()
    val document = ReadAction.compute<Document, Throwable> {
      PsiDocumentManager.getInstance(project).getDocument(psiFile)
    } ?: editorFactory.createDocument("")

    return (editorFactory.createEditor(document, project, fileType, false) as EditorEx).also {
      EditorTextField.setupTextFieldEditor(it)
    }.apply {
      settings.isCaretRowShown = false
      settings.isUseSoftWraps = true

      putUserData(ZoomIndicatorManager.SUPPRESS_ZOOM_INDICATOR, true)
      putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
      colorsScheme.lineSpacing = 1f
      isEmbeddedIntoDialogWrapper = true
      isOneLineMode = oneLine

      component.addPropertyChangeListener("font") {
        setEditorFontFromComponent()
      }
      setEditorFontFromComponent()

      setBorder(null)
      if (!inline) {
        component.border = EditorFocusBorder()
      }
      else if (!oneLine) {
        setVerticalScrollbarVisible(true)
      }
      contentComponent.setFocusCycleRoot(false)
    }
  }

  private fun EditorEx.setEditorFontFromComponent() {
    val font = component.font
    colorsScheme.editorFontName = font.name
    colorsScheme.editorFontSize = font.size
  }
}

// can't use DarculaTextBorderNew because of nested focus and because it's a UIResource
private class EditorFocusBorder : Border, ErrorBorderCapable {
  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val hasFocus = UIUtil.isFocusAncestor(c)

    val rect = Rectangle(x, y, width, height).also {
      val maxBorderThickness = DarculaUIUtil.BW.get()
      JBInsets.removeFrom(it, JBInsets.create(maxBorderThickness, maxBorderThickness))
    }
    DarculaNewUIUtil.fillInsideComponentBorder(g, rect, c.background)
    DarculaNewUIUtil.paintComponentBorder(g, rect, DarculaUIUtil.getOutline(c as JComponent), hasFocus, c.isEnabled)
  }

  // the true vertical inset would be 7, but Editor has 1px padding above and below the line
  override fun getBorderInsets(c: Component): Insets = JBInsets.create(6, 10)
  override fun isBorderOpaque(): Boolean = false
}