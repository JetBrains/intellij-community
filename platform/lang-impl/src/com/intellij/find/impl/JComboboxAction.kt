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
import com.intellij.openapi.observable.properties.AtomicProperty
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

class JComboboxAction(val project: Project, val onChanged: () -> Unit) : AnAction(), CustomComponentAction {
  private val latestMaskProperty = AtomicProperty<String?>(FindSettings.getInstance().fileMask)
  private var latestMask: String? by latestMaskProperty
  val saveMask = { FindSettings.getInstance().fileMask = latestMask }

  override fun createCustomComponent(presentation: Presentation) =
    ComboboxActionComponent(project, latestMaskProperty) { onChanged() }.also { it.isEditable = true }

  override fun actionPerformed(e: AnActionEvent) {}

  class ComboboxActionComponent(private val project: Project,
                                private val mask: AtomicProperty<String?>,
                                private val onChanged: () -> Unit) :
    ComboBox<String>(FindSettings.getInstance().recentFileMasks.reversed().toTypedArray()) {
    private val findModel = FindManager.getInstance(project).findInProjectModel
    private val rebuild = {
      mask.set(getNormalizedText())
      findModel.fileFilter = getNormalizedText()
      onChanged()
    }

    init {
      setEditor(BasicComboBoxEditor())
      maximumRowCount = 12
      prototypeDisplayValue = emptyText
      isOpaque = false
      insertItemAt(emptyText, 0)
      selectedItem = FindSettings.getInstance().fileMask ?: emptyText
      findModel.fileFilter = FindSettings.getInstance().fileMask
      addActionListener(ActionListener { rebuild() })

      (editor.editorComponent as JTextField).also {
        it.background = JBUI.CurrentTheme.BigPopup.searchFieldBackground()
        it.addFocusListener(object : FocusAdapter() {
          override fun focusGained(e: FocusEvent) {
            if (selectedIndex == 0) {
              editor.item = ""
            }
          }

          override fun focusLost(e: FocusEvent) {
            if (it.text.isEmpty()) {
              (editor.editorComponent as JTextField).text = emptyText
              selectedIndex = 0
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

    override fun getPreferredSize() = Dimension(JBUI.scale(120),
                                                ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE.height + insets.top + insets.bottom - JBUI.scale(
                                                  1))

    fun getNormalizedText(): String? {
      val editorField = editor.editorComponent as JTextField
      return if (editorField.text == emptyText || editorField.text.isBlank()) null else editorField.text
    }
  }

  companion object {
    val emptyText = FindBundle.message("se.text.header.action.all.filetypes")
  }
}