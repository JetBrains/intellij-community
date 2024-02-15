// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview

import com.intellij.collaboration.file.codereview.CodeReviewDiffVirtualFile
import com.intellij.diff.editor.DiffEditorViewerFileEditor
import com.intellij.idea.AppMode
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.ProjectManager

object CodeReviewAdvancedSettings {
  internal const val COMBINED_DIFF_SETTING_ID = "enable.combined.diff.for.codereview"

  fun isCombinedDiffEnabled(): Boolean = AdvancedSettings.getBoolean(COMBINED_DIFF_SETTING_ID) && !AppMode.isRemoteDevHost()
}

class CodeReviewCombinedDiffAdvancedSettingsChangeListener : AdvancedSettingsChangeListener {
  override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
    if (id == CodeReviewAdvancedSettings.COMBINED_DIFF_SETTING_ID) {
      for (project in ProjectManager.getInstance().openProjects) {
        DiffEditorViewerFileEditor.reloadDiffEditorsForFiles(project) { it is CodeReviewDiffVirtualFile }
      }
    }
  }
}