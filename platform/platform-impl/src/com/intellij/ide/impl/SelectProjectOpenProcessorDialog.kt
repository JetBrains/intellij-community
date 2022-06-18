// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import javax.swing.JComponent

@ApiStatus.Internal
class SelectProjectOpenProcessorDialog(
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

  companion object {
    private var testShowAndGetChoice: (List<ProjectOpenProcessor>, VirtualFile) -> ProjectOpenProcessor? = { it, _ -> it.firstOrNull() }

    @TestOnly
    fun setTestDialog(showAndGetChoice: (List<ProjectOpenProcessor>, VirtualFile) -> ProjectOpenProcessor?, parentDisposable: Disposable) {
      val prevDialog = testShowAndGetChoice
      testShowAndGetChoice = showAndGetChoice
      Disposer.register(parentDisposable, Disposable { testShowAndGetChoice = prevDialog })
    }

    @JvmStatic
    fun showAndGetChoice(processors: List<ProjectOpenProcessor>, file: VirtualFile): ProjectOpenProcessor? {
      val app = ApplicationManager.getApplication()
      return when {
        app != null && (app.isUnitTestMode || app.isHeadlessEnvironment) -> testShowAndGetChoice(processors, file)
        else -> SelectProjectOpenProcessorDialog(processors, file).showAndGetChoice()
      }
    }
  }
}
