// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository.settings

import com.intellij.ide.JavaUiBundle
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * A dialog prompts a user to select a replacement for [repositoryToRemove] from [allRepositories] or `null`.
 */
internal class RepositoryRemoveDialog(project: Project,
                                      repositoryToRemove: RemoteRepositoryDescription,
                                      allRepositories: List<RemoteRepositoryDescription>,
                                      private val linkedLibrariesCount: Int) : DialogWrapper(project) {
  companion object {
    const val PANEL_MIN_WIDTH = 400
  }

  private val model: CollectionComboBoxModel<RemoteRepositoryDescription>

  init {
    model = CollectionComboBoxModel(listOf(null) + allRepositories.filterNot { it == repositoryToRemove })
    title = JavaUiBundle.message("jar.repository.manager.delegate.repository.usages.dialog.title")
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        comboBox(model, SimpleListCellRenderer.create(JavaUiBundle.message("repository.library.bind.repository.not.selected")) { it.url })
          .align(Align.FILL)
      }
      row {
        comment(JavaUiBundle.message("jar.repository.manager.delegate.repository.usages.dialog.label", linkedLibrariesCount))
      }
    }.apply {
      withMinimumWidth(PANEL_MIN_WIDTH)
      isResizable = false
    }
  }

  fun getSelectedRepository(): RemoteRepositoryDescription? = model.selected
}