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
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.DocumentAdapter
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicComboBoxEditor

@ApiStatus.Internal
class JComboboxAction(val project: Project, val onChanged: () -> Unit) : AnAction(), CustomComponentAction {
  private val latestMaskProperty: AtomicProperty<String?> = AtomicProperty(FindSettings.getInstance().fileMask)
  private var latestMask: String? by latestMaskProperty
  val saveMask: () -> Unit = { FindSettings.getInstance().fileMask = latestMask }

  override fun createCustomComponent(presentation: Presentation, place: String): ComboboxActionComponent =
    ComboboxActionComponent(project, latestMaskProperty) { onChanged() }.also { it.isEditable = true }

  override fun actionPerformed(e: AnActionEvent) {}

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    val comboboxComponent = component as ComboboxActionComponent
    if (FindManager.getInstance(project).findInProjectModel.fileFilter == null
        && comboboxComponent.selectedItem != emptyText
        && comboboxComponent.isFocusOwner) {
      comboboxComponent.selectedItem = emptyText
    }
  }

  class ComboboxActionComponent(project: Project,
                                private val latestMaskProperty: AtomicProperty<String?>,
                                private val onChanged: () -> Unit) :
    ComboBox<String>(FindSettings.getInstance().recentFileMasks.reversed().toTypedArray()) {
    private val findModel = FindManager.getInstance(project).findInProjectModel
    private val rebuild = {
      latestMaskProperty.set(getNormalizedText())
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
      addItemListener { rebuild() }

      findModel.addObserver {
        runInEdt {
          if (findModel.fileFilter == null) selectedItem = emptyText
        }
      }

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

    override fun getPreferredSize(): Dimension =
      Dimension(JBUI.scale(120), ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height + insets.top + insets.bottom)

    private fun getNormalizedText(): String? {
      val editorField = editor.editorComponent as JTextField
      return if (editorField.text == emptyText || editorField.text.isBlank()) null else editorField.text
    }
  }

  companion object {
    val emptyText: @Nls String = FindBundle.message("se.text.header.action.all.filetypes")
  }
}