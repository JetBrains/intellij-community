// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeEditor.printing

import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.ApiStatus
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JRadioButton

@ApiStatus.Internal
class ExportToHTMLDialog(private val fileName: String?,
                         private val directoryName: String?,
                         private val selectedTextEnabled: Boolean,
                         private val project: Project) : DialogWrapper(project, true) {

  companion object {
    @JvmStatic
    fun addBrowseDirectoryListener(targetDirectoryField: TextFieldWithBrowseButton, project: Project) {
      targetDirectoryField.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
        .withTitle(EditorBundle.message("export.to.html.select.output.directory.title"))
        .withDescription(EditorBundle.message("export.to.html.select.output.directory.description")))
    }
  }

  private lateinit var rbCurrentFile: JRadioButton
  private lateinit var rbSelectedText: JRadioButton
  private lateinit var rbCurrentPackage: JRadioButton
  private lateinit var cbIncludeSubpackages: JCheckBox
  private lateinit var cbLineNumbers: JCheckBox
  private lateinit var cbOpenInBrowser: JCheckBox
  private lateinit var targetDirectoryField: TextFieldWithBrowseButton
  private val myExtensions = PrintOption.EP_NAME.extensionList.map(PrintOption::createConfigurable)

  init {
    setOKButtonText(EditorBundle.message("export.to.html.save.button"))
    title = EditorBundle.message("export.to.html.title")
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      buttonsGroup {
        row {
          rbCurrentFile = radioButton(EditorBundle.message("export.to.html.file.name.radio", fileName ?: ""))
            .component
        }
        row {
          rbSelectedText = radioButton(EditorBundle.message("export.to.html.selected.text.radio"))
            .component
        }
        row {
          rbCurrentPackage = radioButton(EditorBundle.message("export.to.html.all.files.in.directory.radio", directoryName ?: ""))
            .component
        }
        indent {
          row {
            cbIncludeSubpackages = checkBox(EditorBundle.message("export.to.html.include.subdirectories.checkbox"))
              .enabledIf(rbCurrentPackage.selected)
              .component
          }
        }
      }

      row {
        val field = FileChooserFactory.getInstance().createFileTextField(FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                                                         myDisposable)
        targetDirectoryField = TextFieldWithBrowseButton(field.getField())
        addBrowseDirectoryListener(targetDirectoryField, project)
        cell(targetDirectoryField)
          .align(AlignX.FILL)
          .label(EditorBundle.message("export.to.html.output.directory.label"), LabelPosition.TOP)
      }

      group(EditorBundle.message("export.to.html.options.group")) {
        row {
          cbLineNumbers = checkBox(EditorBundle.message("export.to.html.options.show.line.numbers.checkbox"))
            .component
        }

        for (printOption in myExtensions) {
          printOption.createComponent()?.let {
            row {
              cell(it).align(AlignX.FILL)
            }
          }
        }

        row {
          cbOpenInBrowser = checkBox(EditorBundle.message("export.to.html.open.generated.html.checkbox")).component
        }
      }
    }
  }

  override fun dispose() {
    for (extension in myExtensions) {
      extension.disposeUIResources()
    }
    super.dispose()
  }

  override fun getHelpId(): String {
    return HelpID.EXPORT_TO_HTML
  }

  fun reset() {
    val exportToHTMLSettings = ExportToHTMLSettings.getInstance(project)
    rbSelectedText.setEnabled(selectedTextEnabled)
    rbSelectedText.setSelected(selectedTextEnabled)
    rbCurrentFile.setEnabled(fileName != null)
    rbCurrentFile.setSelected(fileName != null && !selectedTextEnabled)
    rbCurrentPackage.setEnabled(directoryName != null)
    rbCurrentPackage.setSelected(directoryName != null && !selectedTextEnabled && fileName == null)
    cbIncludeSubpackages.setSelected(exportToHTMLSettings.isIncludeSubdirectories)
    cbIncludeSubpackages.setEnabled(rbCurrentPackage.isSelected)
    cbLineNumbers.setSelected(exportToHTMLSettings.PRINT_LINE_NUMBERS)
    cbOpenInBrowser.setSelected(exportToHTMLSettings.OPEN_IN_BROWSER)
    targetDirectoryField.text = FileUtil.toSystemDependentName(exportToHTMLSettings.OUTPUT_DIRECTORY ?: "")
    for (printOption in myExtensions) {
      printOption.reset()
    }
  }

  @Throws(ConfigurationException::class)
  fun apply() {
    val exportToHTMLSettings = ExportToHTMLSettings.getInstance(project)
    if (rbCurrentFile.isSelected) {
      exportToHTMLSettings.printScope = PrintSettings.PRINT_FILE
    }
    else if (rbSelectedText.isSelected) {
      exportToHTMLSettings.printScope = PrintSettings.PRINT_SELECTED_TEXT
    }
    else if (rbCurrentPackage.isSelected) {
      exportToHTMLSettings.printScope = PrintSettings.PRINT_DIRECTORY
    }
    exportToHTMLSettings.setIncludeSubpackages(cbIncludeSubpackages.isSelected)
    exportToHTMLSettings.PRINT_LINE_NUMBERS = cbLineNumbers.isSelected
    exportToHTMLSettings.OPEN_IN_BROWSER = cbOpenInBrowser.isSelected
    exportToHTMLSettings.OUTPUT_DIRECTORY = FileUtil.toSystemIndependentName(targetDirectoryField.getText())
    for (printOption in myExtensions) {
      printOption.apply()
    }
  }
}
