// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.Nls

@Serializable
data class PluginData(
  val pluginIdString: String = "",
  @NlsSafe val nullablePluginName: String? = null,
  val isBundled: Boolean = false,
  val isFromCustomRepository: Boolean = false,
) : Comparable<PluginData> {
  val pluginId: PluginId
    get() = PluginId.getId(pluginIdString)

  val pluginName: String
    get() = nullablePluginName ?: pluginIdString

  constructor(descriptor: PluginDescriptor) : this(
    descriptor.pluginId.idString,
    descriptor.name,
    descriptor.isBundled,
  )

  override fun compareTo(other: PluginData): Int {
    return if (isBundled && !other.isBundled) -1
    else if (!isBundled && other.isBundled) 1
    else Comparing.compare(pluginIdString, other.pluginIdString)
  }
}

@Serializable
data class FeaturePluginData(
  val displayName: @Nls String = "",
  val pluginData: PluginData = PluginData(),
)

@Serializable
data class PluginDataSet(val dataSet: Set<PluginData> = emptySet())

@Serializable
data class PluginFeatureMap(
  val featureMap: Map<String, PluginDataSet> = emptyMap(),
  val lastUpdateTime: Long = 0L,
) {

  fun get(implementationName: String): Set<PluginData> = featureMap.get(implementationName)?.dataSet ?: emptySet()
}
