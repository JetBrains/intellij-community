// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@Serializable
@ApiStatus.Internal
data class PluginData(
  @JvmField val pluginIdString: String = "",
  @NlsSafe @JvmField val nullablePluginName: String? = null,
  @JvmField val isBundled: Boolean = false,
  @JvmField val isFromCustomRepository: Boolean = false,
) : Comparable<PluginData> {
  val pluginId: PluginId
    get() = PluginId.getId(pluginIdString)

  val pluginName: String
    get() = nullablePluginName ?: pluginIdString

  constructor(descriptor: PluginDescriptor) : this(
    pluginIdString = descriptor.pluginId.idString,
    nullablePluginName = descriptor.name,
    isBundled = descriptor.isBundled,
  )

  override fun compareTo(other: PluginData): Int {
    return if (isBundled && !other.isBundled) -1
    else if (!isBundled && other.isBundled) 1
    else Comparing.compare(pluginIdString, other.pluginIdString)
  }
}

@Serializable
@ApiStatus.Internal
data class FeaturePluginData(
  @JvmField val displayName: @Nls String = "",
  @JvmField val pluginData: PluginData = PluginData(),
)

@Serializable
@ApiStatus.Internal
data class PluginDataSet(@JvmField val dataSet: Set<PluginData> = emptySet())

@Serializable
@ApiStatus.Internal
data class PluginFeatureMap(
  @JvmField val featureMap: Map<String, PluginDataSet> = emptyMap(),
  @JvmField val lastUpdateTime: Long = 0L,
) {
  fun get(implementationName: String): Set<PluginData> = featureMap.get(implementationName)?.dataSet ?: emptySet()
}
