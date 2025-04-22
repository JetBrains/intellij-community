// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors.fileStatus

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.Configurable.VariableProjectAppLevel
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vcs.FileStatusFactory
import com.intellij.openapi.vcs.FileStatusManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

@ApiStatus.Internal
class FileStatusColorsConfigurable : SearchableConfigurable, NoScroll, VariableProjectAppLevel {
  private var myPanel: FileStatusColorsPanel? = null

  override fun getId(): String {
    return FILE_STATUS_COLORS_ID
  }

  override fun getHelpTopic(): String {
    return "reference.versionControl.highlight"
  }

  override fun getDisplayName(): @Nls String {
    return ApplicationBundle.message("title.file.status.colors")
  }

  override fun createComponent(): JComponent? {
    if (myPanel == null) {
      myPanel = FileStatusColorsPanel(FileStatusFactory.getInstance().getAllFileStatuses())
    }
    return myPanel!!.component
  }

  override fun disposeUIResources() {
    if (myPanel != null) {
      myPanel = null
    }
  }

  override fun isModified(): Boolean {
    return myPanel != null && myPanel!!.model.isModified()
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    if (myPanel != null) {
      myPanel!!.model.apply()
      for (project in ProjectManager.getInstance().getOpenProjects()) {
        FileStatusManager.getInstance(project).fileStatusesChanged()
      }
    }
  }

  override fun reset() {
    if (myPanel != null) {
      myPanel!!.model.reset()
    }
  }

  override fun isProjectLevel(): Boolean {
    return false
  }

  companion object {
    private const val FILE_STATUS_COLORS_ID = "file.status.colors"
  }
}
