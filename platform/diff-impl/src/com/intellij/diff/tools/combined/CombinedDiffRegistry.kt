// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.ide.ui.RegistryBooleanOptionDescriptor
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.util.registry.Registry

object CombinedDiffRegistry {
  fun isEnabled(): Boolean = AdvancedSettings.getBoolean("enable.combined.diff") && !AppMode.isRemoteDevHost()

  fun getPreloadedBlocksCount(): Int = Registry.intValue("combined.diff.visible.viewport.delta", 3, 1, 100)

  fun getMaxBlockCountInMemory(): Int = Registry.intValue("combined.diff.loaded.content.limit")

  fun getFilesLimit(): Int = Registry.intValue("combined.diff.files.limit")
}

class CombinedDiffAdvancedSettingsChangeListener : AdvancedSettingsChangeListener {
  companion object {
    private var dialogScheduled = false
  }

  override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
    if (id == "enable.combined.diff" && !dialogScheduled) {
      dialogScheduled = true
      ApplicationManager.getApplication().invokeLater(
        {
          dialogScheduled = false
          RegistryBooleanOptionDescriptor.suggestRestart(null)
        }, ModalityState.nonModal())
    }
  }
}
