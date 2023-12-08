// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.advertiser

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.settings.CacheStateTag
import com.intellij.platform.settings.Setting
import com.intellij.platform.settings.objectSettingValueSerializer
import com.intellij.platform.settings.settingDescriptorFactoryFactory
import org.jetbrains.annotations.ApiStatus

/**
 * Caches the marketplace plugins that support given filenames/extensions or dependencies. This data
 * is persisted between IDE restarts and refreshed on startup if the cached data is more than 1 day old
 * (see PluginsAdvertiserStartupActivity.checkSuggestedPlugins). The cached data potentially includes plugins
 * that are incompatible with the current IDE build.
 */
@Service(Service.Level.APP)
@ApiStatus.Internal
class PluginFeatureCacheService {
  companion object {
    fun getInstance(): PluginFeatureCacheService = service()
  }

  private val serializer = objectSettingValueSerializer<PluginFeatureMap>()

  private val settingGroup = settingDescriptorFactoryFactory(PluginManagerCore.CORE_ID).group(key = "pluginFeatureCache") {
    tags = listOf(CacheStateTag)
  }

  val dependencies: Setting<PluginFeatureMap> = settingGroup.setting("dependencies", serializer)
  val extensions: Setting<PluginFeatureMap> = settingGroup.setting("extensions", serializer)
}