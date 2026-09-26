// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.export

import com.intellij.codeEditor.printing.ExportToHTMLDialog.Companion.addBrowseDirectoryListener
import com.intellij.codeEditor.printing.ExportToHTMLSettings
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.GraphicsUtil
import java.awt.Dimension
import javax.swing.JCheckBox
import javax.swing.JComponent

class ExportToHTMLDialog(private val project: Project, private val canBeOpenInBrowser: Boolean) : DialogWrapper(project, true) {

  private var cbOpenInBrowser: JCheckBox? = null
  private val targetDirectoryField = TextFieldWithBrowseButton()

  init {
    setOKButtonText(InspectionsBundle.message("inspection.export.save.button"))
    title = InspectionsBundle.message("inspection.export.dialog.title")
    init()
  }

  override fun createCenterPanel(): JComponent {
    addBrowseDirectoryListener(targetDirectoryField, project)

    return panel {
      row {
        cell(targetDirectoryField)
          .label(EditorBundle.message("export.to.html.output.directory.label"), LabelPosition.TOP)
          .align(AlignX.FILL)
      }

      if (canBeOpenInBrowser) {
        row {
          cbOpenInBrowser = checkBox(InspectionsBundle.message("inspection.export.open.option")).component
        }
      }
    }
  }

  fun reset() {
    val exportToHTMLSettings = ExportToHTMLSettings.getInstance(project)
    cbOpenInBrowser?.isSelected = exportToHTMLSettings.OPEN_IN_BROWSER
    val text = exportToHTMLSettings.OUTPUT_DIRECTORY
    targetDirectoryField.text = text
    if (text != null) {
      targetDirectoryField.preferredSize = Dimension(GraphicsUtil.stringWidth(text, targetDirectoryField.font) + 100,
                                                     targetDirectoryField.getPreferredSize().height)
    }
  }

  fun apply() {
    val exportToHTMLSettings = ExportToHTMLSettings.getInstance(project)
    cbOpenInBrowser?.let {
      exportToHTMLSettings.OPEN_IN_BROWSER = it.isSelected
    }
    exportToHTMLSettings.OUTPUT_DIRECTORY = targetDirectoryField.getText()
  }

  override fun getHelpId(): String {
    return "procedures.inspecting.export"
  }
}
