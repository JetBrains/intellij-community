// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.editor.DiffEditorViewerFileEditor.Companion.reloadDiffEditorsForFiles
import com.intellij.diff.editor.DiffViewerVirtualFile
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry

object CombinedDiffRegistry {
  internal const val COMBINED_DIFF_SETTING_ID = "enable.combined.diff"

  fun isEnabled(): Boolean {
    return AdvancedSettings.getBoolean(COMBINED_DIFF_SETTING_ID) && !AppMode.isRemoteDevHost()
  }

  fun setCombinedDiffEnabled(enabled: Boolean) {
    AdvancedSettings.setBoolean(COMBINED_DIFF_SETTING_ID, enabled)

    if (enabled) {
      resetBadge()
    }
  }

  fun getPreloadedBlocksCount(): Int = Registry.intValue("combined.diff.visible.viewport.delta", 3, 1, 100)

  fun getMaxBlockCountInMemory(): Int = Registry.intValue("combined.diff.loaded.content.limit")

  fun getFilesLimit(): Int = Registry.intValue("combined.diff.files.limit")

  private const val BADGE_ID = "combined.diff.badge.shown"

  fun showBadge(): Boolean = !PropertiesComponent.getInstance().getBoolean(BADGE_ID, false)

  fun resetBadge() = PropertiesComponent.getInstance().setValue(BADGE_ID, true)
}

class CombinedDiffAdvancedSettingsChangeListener : AdvancedSettingsChangeListener {
  override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
    if (id == CombinedDiffRegistry.COMBINED_DIFF_SETTING_ID) {
      for (project in ProjectManager.getInstance().openProjects) {
        reloadDiffEditorsForFiles(project) { it is DiffViewerVirtualFile }
      }
    }
  }
}
