// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.advertiser

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.settings.CacheTag
import com.intellij.platform.settings.Setting
import com.intellij.platform.settings.objectSerializer
import com.intellij.platform.settings.settingDescriptorFactory
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

  private val factory = settingDescriptorFactory(PluginManagerCore.CORE_ID)
  private val serializer = factory.objectSerializer<PluginFeatureMap>()
  private val settingGroup = factory.group(groupKey = "pluginFeatureCache") {
    tags = listOf(CacheTag)
  }

  val dependencies: Setting<PluginFeatureMap> = settingGroup.setting("dependencies", serializer)
  val extensions: Setting<PluginFeatureMap> = settingGroup.setting("extensions", serializer)
}