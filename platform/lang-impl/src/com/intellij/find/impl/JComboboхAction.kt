// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.find.FindSettings
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.DocumentAdapter
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.event.ActionListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicComboBoxEditor

class JComboboxAction(val project: Project, onChanged: () -> Unit) : AnAction(), CustomComponentAction {
  private val combobox: ComboBox<String> = ComboboxActionComponent(FindSettings.getInstance().recentFileMasks.reversed())
  private var editor: JTextField
  private val emptyText = FindBundle.message("se.text.header.action.all.filetypes")
  private val findModel = FindManager.getInstance(project).findInProjectModel

  private val rebuild = {
    findModel.fileFilter = getMask()
    onChanged()
  }

  init {
    combobox.apply {
      editor = BasicComboBoxEditor()
      isEditable = true
      maximumRowCount = 12
      isOpaque = false
      insertItemAt(emptyText, 0)
      selectedItem = FindSettings.getInstance().fileMask ?: emptyText
      findModel.fileFilter = FindSettings.getInstance().fileMask
      addActionListener(ActionListener {
        FindSettings.getInstance().fileMask = getMask()
        rebuild()
      })
    }

    editor = combobox.editor.editorComponent as JTextField
    editor.also {
      it.background = JBUI.CurrentTheme.BigPopup.searchFieldBackground()
      it.addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent) {
          if (combobox.selectedIndex == 0) {
            combobox.editor.item = ""
          }
        }

        override fun focusLost(e: FocusEvent) {
          if (it.text.isEmpty()) {
            combobox.selectedIndex = 0
          }
        }
      })

      it.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          rebuild()
        }
      })
    }
  }

  override fun actionPerformed(e: AnActionEvent) {}
  override fun createCustomComponent(presentation: Presentation) = combobox

  private fun getMask() = if (editor.text == emptyText || editor.text.isBlank()) null else editor.text

  class ComboboxActionComponent(list: List<String>) : ComboBox<String>(list.toTypedArray()) {
    override fun getPreferredSize() = Dimension(super.getPreferredSize().width,
                                                ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE.height + insets.top + insets.bottom)
  }
}