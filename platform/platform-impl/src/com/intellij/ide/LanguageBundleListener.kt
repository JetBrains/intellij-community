// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.DynamicBundle
import com.intellij.l10n.LocalizationStateService
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.util.text.DateTimeFormatManager
import java.nio.file.Files
import java.nio.file.NoSuchFileException

private class LanguageBundleListener : AppLifecycleListener {
  override fun appStarted() {
    LocalizationStateService.getInstance()?.let {
      (it as PersistentStateComponent<*>).initializeComponent()
      it.resetLocaleIfNeeded()
    }
    EarlyAccessRegistryManager.syncAndFlush()
    DynamicBundle.clearCache()
    DateTimeFormatManager.getInstance().resetFormats()
  }

  
  /**
   * Reads the early access registry file and updates the registry settings related to localization.
   * This ensures that any changes made externally (e.g., via Toolbox) to the localization settings are accurately reflected. 
   * */
  override fun appClosing() {
    val earlyAccessRegistryFile = PathManager.getConfigDir().resolve(EarlyAccessRegistryManager.fileName)
    val lines = try {
      Files.lines(earlyAccessRegistryFile)
    }
    catch (_: NoSuchFileException) {
      return
    }
    lines.use { lineStream ->
      val iterator = lineStream.iterator()
      while (iterator.hasNext()) {
        val key = iterator.next()
        if (!iterator.hasNext()) {
          break
        }
        val value = iterator.next()
        if (key == LocalizationUtil.LOCALIZATION_KEY) {
          EarlyAccessRegistryManager.setString(LocalizationUtil.LOCALIZATION_KEY, value)
          break
        }
      }
    }
  }
}
