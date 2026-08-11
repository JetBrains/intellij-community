// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui

import com.intellij.dvcs.DvcsRememberedInputs
import com.intellij.dvcs.repo.ClonePathProvider
import com.intellij.dvcs.ui.DvcsBundle.message
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.progress.ComponentVisibilityProgressManager
import javax.swing.JButton
import javax.swing.JLabel

internal class CloneDvcsDialogUi(project: Project, rememberedInputs: DvcsRememberedInputs) {

  @JvmField
  val repositoryUrlComboboxModel: CollectionComboBoxModel<String> = CollectionComboBoxModel()

  @JvmField
  val repositoryUrlField: TextFieldWithAutoCompletion<String> =
    TextFieldWithAutoCompletion.create(project, repositoryUrlComboboxModel.items, false, "")

  @JvmField
  val repositoryUrlFieldSpinner: JLabel = JLabel(AnimatedIcon.Default())

  @JvmField
  val spinnerProgressManager: ComponentVisibilityProgressManager =
    ComponentVisibilityProgressManager(repositoryUrlFieldSpinner)

  @JvmField
  val repositoryUrlCombobox: ComboBox<String> = ComboBox()

  @JvmField
  val testButton: JButton = JButton(message("clone.repository.url.test.label"))

  @JvmField
  val directoryField: CloneDvcsDialog.MyTextFieldWithBrowseButton =
    CloneDvcsDialog.MyTextFieldWithBrowseButton(ClonePathProvider.defaultParentDirectoryPath(project, rememberedInputs))

  @JvmField
  val panel: DialogPanel = panel {
    row(message("clone.repository.url.label")) {
      cell(repositoryUrlCombobox).align(AlignX.FILL).resizableColumn()
      cell(testButton)
    }
    row(message("clone.destination.directory.label")) {
      cell(directoryField).align(AlignX.FILL)
    }
  }.withPreferredWidth(500)
}
