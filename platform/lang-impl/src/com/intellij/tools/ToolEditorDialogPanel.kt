// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import javax.swing.JPanel
import javax.swing.JTextField

private const val ADVANCED_OPTIONS_EXPANDED_KEY = "ExternalToolDialog.advanced.expanded"
private const val ADVANCED_OPTIONS_EXPANDED_DEFAULT = false

internal class ToolEditorDialogPanel {

  @JvmField
  val panel = panel {
    row(ToolsBundle.message("label.tool.name")) {
      nameField = textField()
        .align(AlignX.FILL)
        .resizableColumn()
        .focused()
        .component

      groupCombo = comboBox(emptyList<String>())
        .align(AlignX.FILL)
        .resizableColumn()
        .label(ToolsBundle.message("label.tool.group"))
        .applyToComponent { isEditable = true }
        .component
    }

    row(ToolsBundle.message("label.tool.description")) {
      descriptionField = textField()
        .align(AlignX.FILL)
        .component
    }

    group(ToolsBundle.message("border.title.tool.settings")) {
      row(ToolsBundle.message("label.tool.program")) {
        programField = textFieldWithBrowseButton()
          .align(AlignX.FILL)
          .component
      }
      row(ToolsBundle.message("label.tool.arguments")) {
        argumentsField = cell(RawCommandLineEditor())
          .align(AlignX.FILL)
          .component
      }
      row(ToolsBundle.message("label.tool.working.directory")) {
        workingDirField = textFieldWithBrowseButton()
          .align(AlignX.FILL)
          .component
      }
    }.bottomGap(BottomGap.NONE)

    row {
      additionalOptionsPanel = cell(JPanel())
        .align(AlignX.FILL)
        .component
    }

    collapsibleGroup(ToolsBundle.message("dialog.separator.advanced.options")) {
      row {
        synchronizedAfterRunCheckbox = checkBox(ToolsBundle.message("checkbox.synchronize.files.after.execution"))
          .component
      }
      row {
        useConsoleCheckbox = checkBox(ToolsBundle.message("checkbox.open.console.for.tool.output"))
          .component
      }
      indent {
        row {
          showConsoleOnStdOutCheckbox = checkBox(ToolsBundle.message("checkbox.make.console.active.on.message.in.stdout"))
            .component
        }
        row {
          showConsoleOnStdErrCheckbox = checkBox(ToolsBundle.message("checkbox.make.console.active.on.message.in.stderr"))
            .component
        }
      }.enabledIf(useConsoleCheckbox.selected)

      row(ToolsBundle.message("label.output.filters")) {
        outputFilterField = cell(RawCommandLineEditor(ToolEditorDialog.OUTPUT_FILTERS_SPLITTER, ToolEditorDialog.OUTPUT_FILTERS_JOINER))
          .comment(ToolsBundle.message("label.each.line.is.a.regex.available.macros.file.path.line.and.column"),
                   maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
          .align(AlignX.FILL)
          .component
      }
    }.apply {
      expanded = PropertiesComponent.getInstance().getBoolean(ADVANCED_OPTIONS_EXPANDED_KEY, ADVANCED_OPTIONS_EXPANDED_DEFAULT)
      packWindowHeight = true
      addExpandedListener {
        PropertiesComponent.getInstance().setValue(ADVANCED_OPTIONS_EXPANDED_KEY, it, ADVANCED_OPTIONS_EXPANDED_DEFAULT)
      }
    }
  }

  lateinit var nameField: JTextField
  lateinit var groupCombo: ComboBox<String>
  lateinit var descriptionField: JTextField
  lateinit var programField: TextFieldWithBrowseButton
  lateinit var argumentsField: RawCommandLineEditor
  lateinit var workingDirField: TextFieldWithBrowseButton
  lateinit var additionalOptionsPanel: JPanel
  lateinit var synchronizedAfterRunCheckbox: JBCheckBox
  lateinit var useConsoleCheckbox: JBCheckBox
  lateinit var showConsoleOnStdOutCheckbox: JBCheckBox
  lateinit var showConsoleOnStdErrCheckbox: JBCheckBox
  lateinit var outputFilterField: RawCommandLineEditor

}
