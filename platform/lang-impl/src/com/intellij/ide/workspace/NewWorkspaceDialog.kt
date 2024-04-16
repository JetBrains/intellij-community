// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManager
import com.intellij.lang.LangBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.Action
import javax.swing.JComponent

class NewWorkspaceDialog(
  val project: Project
) : DialogWrapper(project) {
  private lateinit var nameField: JBTextField
  private lateinit var locationField: TextFieldWithBrowseButton

  val projectName: String get() = nameField.text
  val location: Path get() = Paths.get(locationField.text)
  val projectPath: Path get() = location.resolve(nameField.text)

  init {
    title = LangBundle.message("new.workspace.dialog.title")
    okAction.putValue(Action.NAME, IdeBundle.message("button.create"))
    init()
  }

  override fun createCenterPanel(): JComponent {
    val suggestLocation = RecentProjectsManager.getInstance().suggestNewProjectLocation()
    val defaultName = LangBundle.message("new.workspace.dialog.default.workspace.name")
    val suggestName = FileUtil.createSequentFileName(File(suggestLocation), defaultName, "") { !it.exists() }

    // TODO: com.intellij.ide.wizard.NewProjectWizardBaseStep
    return panel {
      row(LangBundle.message("new.workspace.dialog.name.label")) {
        nameField = textField()
          .text(suggestName)
          .columns(COLUMNS_MEDIUM)
          .horizontalAlign(HorizontalAlign.FILL)
          .component
      }
      row(LangBundle.message("new.workspace.dialog.location.label")) {
        val descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
        descriptor.isHideIgnored = false
        descriptor.title = LangBundle.message("chooser.title.select.file.or.directory.to.import")

        locationField = textFieldWithBrowseButton(project = project, fileChooserDescriptor = descriptor)
          .text(suggestLocation)
          .columns(COLUMNS_MEDIUM)
          .horizontalAlign(HorizontalAlign.FILL)
          .component
      }
      row {
        comment(LangBundle.message("new.workspace.dialog.hint"), maxLineLength = 60)
      }
    }
  }

  override fun doOKAction() {
    RecentProjectsManager.getInstance().setLastProjectCreationLocation(location)
    super.doOKAction()
  }
}