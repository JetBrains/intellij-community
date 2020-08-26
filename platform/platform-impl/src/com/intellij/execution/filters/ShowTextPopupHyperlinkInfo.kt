// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.EditorTextField
import java.awt.Dimension
import javax.swing.ScrollPaneConstants

/**
 * A console hyperlink, which shows a popup with the given text on a click.
 * Useful for filters - to create links for viewing contents of argument files.
 *
 * @see ArgumentFileFilter
 */
class ShowTextPopupHyperlinkInfo(@NlsContexts.PopupTitle private val title: String, private val text: String) : HyperlinkInfo {
  override fun navigate(project: Project) {
    ApplicationManager.getApplication().invokeLater {
      val document = EditorFactory.getInstance().createDocument(StringUtil.convertLineSeparators(text))
      val textField = object : EditorTextField(document, project, FileTypes.PLAIN_TEXT, true, false) {
        override fun createEditor(): EditorEx {
          val editor = super.createEditor()
          editor.scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
          editor.scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
          editor.settings.isUseSoftWraps = true
          return editor
        }
      }

      WindowManager.getInstance().getFrame(project)?.size?.let {
        textField.preferredSize = Dimension(it.width / 2, it.height / 2)
      }

      JBPopupFactory.getInstance()
        .createComponentPopupBuilder(textField, textField)
        .setTitle(title)
        .setResizable(true)
        .setMovable(true)
        .setRequestFocus(true)
        .createPopup()
        .showCenteredInCurrentWindow(project)
    }
  }
}