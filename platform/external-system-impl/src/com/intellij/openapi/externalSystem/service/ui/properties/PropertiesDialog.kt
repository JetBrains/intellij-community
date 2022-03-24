// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.properties

import com.intellij.execution.util.setEmptyState
import com.intellij.openapi.externalSystem.service.ui.util.ObservableDialogWrapper
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign

class PropertiesDialog(
  private val project: Project,
  private val info: PropertiesInfo
) : ObservableDialogWrapper(project) {

  private val table = PropertiesTable()

  var properties by table::properties

  override fun configureCenterPanel(panel: Panel) {
    with(panel) {
      row {
        label(info.dialogLabel)
      }
      row {
        table.setEmptyState(info.dialogEmptyState)
        cell(table.component)
          .horizontalAlign(HorizontalAlign.FILL)
      }
    }
  }

  override fun doOKAction() {
    val validationInfo = validateProperties()
    if (validationInfo != null) {
      val title = ExternalSystemBundle.message("external.system.properties.error.title")
      Messages.showErrorDialog(project, validationInfo.message, title)
      return
    }
    super.doOKAction()
  }

  private fun validateProperties(): ValidationInfo? {
    if (table.properties.any { it.name.isEmpty() }) {
      return ValidationInfo(ExternalSystemBundle.message("external.system.properties.error.empty.message"))
    }
    if (table.properties.any { it.name.contains(' ') }) {
      return ValidationInfo(ExternalSystemBundle.message("external.system.properties.error.space.message"))
    }
    if (table.properties.any { it.name.contains('=') }) {
      return ValidationInfo(ExternalSystemBundle.message("external.system.properties.error.assign.message"))
    }
    return null
  }

  init {
    title = info.dialogTitle
    setOKButtonText(info.dialogOkButton)
    init()
  }
}