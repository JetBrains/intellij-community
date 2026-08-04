// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.componentPanelTestAction

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.EditorComboBoxEditor
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.net.MalformedURLException
import java.net.URL
import javax.swing.Icon
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicComboBoxEditor

private val STRING_VALUES = arrayOf("One", "Two", "Three", "Four", "Five", "Six")

internal fun createValidatorsPanel(project: Project, disposable: Disposable): DialogPanel {
  // JTextField component with browse button
  val tfbb = TextFieldWithBrowseButton { println("JTextField browse button pressed") }
  ComponentValidator(disposable)
    .withValidator {
      if (tfbb.text.length < 50) ValidationInfo("${tfbb.text} is too short. Enter at least 50 symbols", tfbb) else null
    }
    .withOutlineProvider(ComponentValidator.CWBB_PROVIDER)
    .andStartOnFocusLost()
    .installOn(tfbb)
  tfbb.textField.document.addDocumentListener(object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) {
      ComponentValidator.getInstance(tfbb).ifPresent(ComponentValidator::revalidate)
    }
  })

  // EditorTextField component with browse button
  val editor = EditorTextField()
  val etfbb = ComponentWithBrowseButton(editor) { println("JTextField browse button pressed") }
  ComponentValidator(disposable)
    .withValidator {
      try {
        URL(etfbb.childComponent.document.text)
        null
      }
      catch (_: MalformedURLException) {
        ValidationInfo("Enter a valid URL", etfbb)
      }
    }
    .withOutlineProvider(ComponentValidator.CWBB_PROVIDER)
    .andStartOnFocusLost()
    .installOn(etfbb)
  etfbb.childComponent.document.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
    override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
      ComponentValidator.getInstance(etfbb).ifPresent(ComponentValidator::revalidate)
    }
  }, disposable)

  // EditorComboBoxEditor
  val comboBox = ComboBox(STRING_VALUES)
  comboBox.setEditor(EditorComboBoxEditor(project, FileTypes.PLAIN_TEXT))
  comboBox.addActionListener { ComponentValidator.getInstance(comboBox).ifPresent(ComponentValidator::revalidate) }
  ComponentValidator(disposable)
    .withValidator { if (comboBox.selectedIndex % 2 == 0) ValidationInfo("Can't select odd items", comboBox) else null }
    .installOn(comboBox)

  // Extendable ComboBox
  val browseExtension = ExtendableTextComponent.Extension.create(
    AllIcons.General.OpenDisk, AllIcons.General.OpenDiskHover, "Open file"
  ) { println("Browse file clicked") }

  val eComboBox = ComboBox(STRING_VALUES)
  val eComboBoxValidator = ComponentValidator(disposable).withValidator {
    val text = eComboBox.editor.item?.toString() ?: ""
    when {
      text.isBlank() -> ValidationInfo("Blank lines are not supported", eComboBox)
      text.contains("e") -> ValidationInfo("Letter 'e' is prohibited", eComboBox)
      else -> null
    }
  }
  eComboBoxValidator.installOn(eComboBox)
  eComboBoxValidator.revalidate() // needed because text is already set
  eComboBox.addActionListener { eComboBoxValidator.revalidate() }
  eComboBox.isEditable = true
  eComboBox.setEditor(object : BasicComboBoxEditor() {
    override fun createEditorComponent(): JTextField {
      val ecbEditor = ExtendableTextField()
      ecbEditor.addExtension(browseExtension)
      ecbEditor.border = JBUI.Borders.empty()
      ecbEditor.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(event: DocumentEvent) {
          eComboBoxValidator.revalidate()
        }
      })
      return ecbEditor
    }
  })

  val animatedIconComboBox = ComboBox<String>()
  animatedIconComboBox.isEditable = true
  animatedIconComboBox.setEditor(object : BasicComboBoxEditor() {
    override fun createEditorComponent(): JTextField {
      val cbEditor = ExtendableTextField()
      cbEditor.addExtension(object : ExtendableTextComponent.Extension {
        private val icon = AnimatedIcon.FS()
        override fun getIcon(hovered: Boolean): Icon = if (!hovered) icon else AllIcons.Process.FS.Step_passive
        override fun getTooltip(): String = "Refresh"
      })
      cbEditor.isEditable = false
      cbEditor.border = JBUI.Borders.empty()
      return cbEditor
    }
  })

  return panel {
    row("Default directory:") {
      cell(tfbb).comment("Preselected in \"Open ...\" and \"New | Project\" dialogs").align(AlignX.FILL)
    }
    row("&EditorTextField:") {
      cell(etfbb).comment("EditorTextField with browse button").align(AlignX.FILL)
    }
    row("&ComboBoxEditorTextField:") {
      cell(comboBox).comment("EditorComboBox editor").align(AlignX.FILL)
    }
    row("ComboBox &extendable:") {
      cell(eComboBox).comment("ComboBox with ExtendableTextEditor").align(AlignX.FILL)
    }
    row("&Animated combobox:") {
      cell(animatedIconComboBox).comment("ComboBox with animated icon").align(AlignX.FILL)
    }
  }
}
