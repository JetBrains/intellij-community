// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.ui.layout.*
import javax.swing.JComponent

internal class SelectProjectOpenProcessorDialog(
  val processors: List<ProjectOpenProcessor>,
  val file: VirtualFile
) : DialogWrapper(null, false) {

  private var selectedProcessor: ProjectOpenProcessor = processors.first()

  init {
    title = ProjectBundle.message("project.open.select.from.multiple.processors.dialog.title")
    init()
  }

  override fun createCenterPanel(): JComponent? = panel {
    row {
      label(ProjectBundle.message("project.open.select.from.multiple.processors.dialog.description.line1", processors.size, file.name))
    }
    row {
      label(ProjectBundle.message("project.open.select.from.multiple.processors.dialog.description.line2"))
    }
    buttonGroup(::selectedProcessor) {
      processors.forEach { processor ->
        row {
          radioButton(ProjectBundle.message("project.open.select.from.multiple.processors.dialog.choice", processor.name), processor)
        }
      }
    }
  }

  override fun getHelpId(): String? = "project.open.select.from.multiple.providers"

  fun showAndGetChoice(): ProjectOpenProcessor? = if (showAndGet()) selectedProcessor else null
}