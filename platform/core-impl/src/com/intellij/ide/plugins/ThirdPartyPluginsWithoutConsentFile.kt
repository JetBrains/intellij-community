// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginManagerCore.logger
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
object ThirdPartyPluginsWithoutConsentFile {
  private const val ALIEN_PLUGINS_FILE = "alien_plugins.txt"

  private val alienPluginsFilePath: Path get() = PathManager.getConfigDir().resolve(ALIEN_PLUGINS_FILE)

  @JvmStatic
  fun appendAliens(pluginIds: Collection<PluginId>) {
    PluginStringSetFile.appendIdsSafe(alienPluginsFilePath, pluginIds.toSet(), logger)
  }

  @JvmStatic
  fun consumeAliensFile(): Set<PluginId> = PluginStringSetFile.consumeIdsSafe(alienPluginsFilePath, logger)

  @JvmStatic
  fun giveConsentToSpecificThirdPartyPlugins(acceptedPlugins: Set<PluginId>) {
    val notAcceptedThirdPartyPluginIds = consumeAliensFile() - acceptedPlugins
    if (notAcceptedThirdPartyPluginIds.isNotEmpty()) {
      appendAliens(notAcceptedThirdPartyPluginIds)
    }
  }
}