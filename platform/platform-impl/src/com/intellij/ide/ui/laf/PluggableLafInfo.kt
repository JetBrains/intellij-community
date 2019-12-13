// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf

import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.EditorTextField
import javax.swing.JComponent
import javax.swing.LookAndFeel
import javax.swing.UIDefaults
import javax.swing.UIManager
import javax.swing.text.JTextComponent

abstract class PluggableLafInfo(name: String, className: String) : UIManager.LookAndFeelInfo(name, className) {
  class SearchAreaContext(val searchComponent: JComponent, val textComponent: JTextComponent,
                          val iconsPanel: JComponent, val scrollPane: JComponent)
  /**
   * Creates an instance of the LookAndFeel provided by the plugin.
   */
  abstract fun createLookAndFeel() : LookAndFeel

  /**
   * Returns <code>true</code> if the LookAndFeel is dark, <code>false</code> otherwise.
   */
  open fun isDark() : Boolean = false

  /**
   * Creates paint context needed to render `SearchTextArea` with this look and feel.
   * @param context is wrapper object that contains all fields that may potentially be needed for
   * rendering.
   * @return a new instance of the painter.
   */
  abstract fun createSearchAreaPainter(context: SearchAreaContext): SearchTextAreaPainter

  /**
   * Creates an instance of `Border` that paints decorations and focus border
   * around EditorTextField
   * @param editorTextField
   * @param editor
   * @return a new instance of the border.
   */
  abstract fun createEditorTextFieldBorder(editorTextField: EditorTextField, editor: EditorEx): DarculaEditorTextFieldBorder
}