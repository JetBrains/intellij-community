// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.ModificationTracker
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.concurrent.TimeUnit

@Serializable
data class PluginData(
  val pluginIdString: String = "",
  private val nullablePluginName: String? = null,
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
  val displayName: String = "",
  val pluginData: PluginData = PluginData()
)

private fun convertMap(map: Map<String, MutableSet<PluginData>>): MutableMap<String, PluginDataSet> {
  val result = HashMap<String, PluginDataSet>()
  for (entry in map.entries) {
    result.put(entry.key, PluginDataSet(entry.value))
  }
  return result
}

@Serializable
data class PluginDataSet(val dataSet: MutableSet<PluginData> = HashSet())

@Serializable
data class PluginFeatureMap(
  val featureMap: MutableMap<String, PluginDataSet> = HashMap(),
  var lastUpdateTime: Long = 0L
) : ModificationTracker {
  @Transient
  private var modificationCount = 0L

  constructor(map: Map<String, MutableSet<PluginData>>) : this(convertMap(map))

  fun update(newFeatureMap: Map<String, Set<PluginData>>) {
    for ((id, plugins) in newFeatureMap) {
      featureMap.computeIfAbsent(id) { PluginDataSet() }.dataSet.addAll(plugins)
    }
    lastUpdateTime = System.currentTimeMillis()
    modificationCount++
  }

  val outdated: Boolean
    get() = System.currentTimeMillis() - lastUpdateTime > TimeUnit.DAYS.toMillis(1L)

  fun get(implementationName: String): Set<PluginData> = featureMap.get(implementationName)?.dataSet ?: emptySet()

  override fun getModificationCount(): Long = modificationCount
}