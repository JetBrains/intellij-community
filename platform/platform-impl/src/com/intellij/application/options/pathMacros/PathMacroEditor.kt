// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.pathMacros

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import java.io.File
import java.io.IOException
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class PathMacroEditor(
  project: Project?,
  title: @NlsContexts.DialogTitle String,
  macroName: @NlsSafe String,
  value: @NlsSafe String,
  private val validator: Validator,
) : DialogWrapper(true) {
  private val nameField = JTextField()
  private val valueField = TextFieldWithBrowseButton()

  interface Validator {
    fun checkName(name: String): Boolean

    fun isOK(name: String, value: String): Boolean
  }

  init {
    setTitle(title)
    nameField.text = macroName
    val documentListener = object : DocumentAdapter() {
      override fun textChanged(event: DocumentEvent) {
        updateControls()
      }
    }
    nameField.document.addDocumentListener(documentListener)
    valueField.text = value
    val descriptor = FileChooserDescriptor(false, true, true, false, true, false)
    valueField.addBrowseFolderListener(project, descriptor, object : TextComponentAccessor<JTextField> {
      override fun getText(component: JTextField): String = component.text

      override fun setText(component: JTextField, text: String) {
        var normalized = text
        val len = normalized.length
        if (len > 0 && normalized[len - 1] == File.separatorChar) {
          normalized = normalized.substring(0, len - 1)
        }
        component.text = normalized
      }
    })
    valueField.textField.document.addDocumentListener(documentListener)

    init()
    updateControls()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(ApplicationBundle.message("editbox.path.macro.name")) {
        cell(nameField)
          .align(AlignX.FILL)
          .focused()
      }
      row(ApplicationBundle.message("editbox.path.macro.value")) {
        cell(valueField)
          .align(AlignX.FILL)
          .columns(COLUMNS_MEDIUM)
      }
    }
  }

  fun setMacroNameEditable(isEditable: Boolean) {
    nameField.isEditable = isEditable
  }

  private fun updateControls() {
    val isNameOK = validator.checkName(nameField.text)
    okAction.isEnabled = isNameOK
    if (isNameOK) {
      val text = valueField.text.trim()
      okAction.isEnabled = text.isNotEmpty() && text != "/"
    }
  }

  override fun getPreferredFocusedComponent(): JComponent = nameField

  override fun getHelpId(): String = PathMacroConfigurable.HELP_ID

  override fun doOKAction() {
    if (!validator.isOK(getName(), getValue())) {
      return
    }
    super.doOKAction()
  }

  fun getName(): String = nameField.text.trim()

  fun getValue(): String {
    val path = valueField.text.trim()
    val file = File(path)
    if (file.isAbsolute) {
      try {
        return file.canonicalPath
      }
      catch (ignored: IOException) {
      }
    }
    return path
  }
}
