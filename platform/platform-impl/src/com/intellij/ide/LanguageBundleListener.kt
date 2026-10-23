// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.DynamicBundle
import com.intellij.l10n.LocalizationStateService
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.text.DateTimeFormatManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Files
import java.nio.file.NoSuchFileException

@ApiStatus.Internal
@VisibleForTesting
class LanguageBundleListener : AppLifecycleListener {
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
    val earlyAccessRegistryFile = PathManager.getOriginalConfigDir().resolve(EarlyAccessRegistryManager.fileName)
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
          // skip the write when the value is the same, because a registry write notifies every registry listener
          val currentValue = ApplicationManager.getApplication().serviceIfCreated<RegistryManager>()?.stringValue(key, "")
          if (currentValue != value) {
            EarlyAccessRegistryManager.setString(LocalizationUtil.LOCALIZATION_KEY, value)
          }
          break
        }
      }
    }
  }
}
