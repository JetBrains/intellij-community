// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.ui.layout.*
import javax.swing.JComponent

class SelectProjectOpenProcessorDialog(
  val providers: List<ProjectOpenProcessor>,
  val file: VirtualFile
) : DialogWrapper(null, false) {

  private var selectedProvider: ProjectOpenProcessor? = null

  init {
    title = ProjectBundle.message("project.open.select.from.multiple.processors.dialog.title")
    init()
  }

  override fun createCenterPanel(): JComponent? {
    return panel {
      row {
        label(ProjectBundle.message("project.open.select.from.multiple.processors.dialog.description.line1",
                                    providers.size, file.name))
      }
      row {
        label(ProjectBundle.message("project.open.select.from.multiple.processors.dialog.description.line2"))
      }
      buttonGroup(::selectedProvider.toNullableBinding(providers.first())) {
        providers.forEach { provider ->
          row {
            radioButton(ProjectBundle.message("project.open.select.from.multiple.processors.dialog.choice", provider.name),
                        provider)
          }
        }
      }
    }
  }

  override fun getHelpId(): String? = "project.open.select.from.multiple.providers"

  fun showAndGetChoice(): ProjectOpenProcessor? {
    return if (showAndGet()) selectedProvider else null
  }
}