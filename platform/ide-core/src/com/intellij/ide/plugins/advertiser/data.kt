// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.xmlb.annotations.*
import java.util.concurrent.TimeUnit

@Tag("plugin")
class PluginData @JvmOverloads constructor(
  @Attribute("pluginId") val pluginIdString: String = "",
  @Attribute("pluginName") private val nullablePluginName: String? = null,
  @Attribute("bundled") val isBundled: Boolean = false,
  @Attribute("fromCustomRepository") val isFromCustomRepository: Boolean = false,
) : Comparable<PluginData> {

  val pluginId: PluginId get() = PluginId.getId(pluginIdString)

  val pluginName: String get() = nullablePluginName ?: pluginIdString

  constructor(descriptor: PluginDescriptor) : this(
    descriptor.pluginId.idString,
    descriptor.name,
    descriptor.isBundled,
  )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PluginData
    return isBundled == other.isBundled
           && pluginIdString == other.pluginIdString
           && (nullablePluginName == null || nullablePluginName == other.nullablePluginName)
  }

  override fun hashCode(): Int {
    var result = pluginIdString.hashCode()
    result = 31 * result + isBundled.hashCode()
    result = 31 * result + (nullablePluginName?.hashCode() ?: 0)
    return result
  }

  override fun compareTo(other: PluginData): Int {
    return if (isBundled && !other.isBundled) -1
    else if (!isBundled && other.isBundled) 1
    else Comparing.compare(pluginIdString, other.pluginIdString)
  }
}

@Tag("featurePlugin")
class FeaturePluginData @JvmOverloads constructor(
  @Attribute("displayName") val displayName: String = "",
  @Property(surroundWithTag = false) val pluginData: PluginData = PluginData(),
)

@Tag("plugins")
class PluginDataSet @JvmOverloads constructor(dataSet: Set<PluginData> = emptySet()) {
  @JvmField
  @XCollection(style = XCollection.Style.v2)
  val dataSet = HashSet<PluginData>()

  init {
    this.dataSet.addAll(dataSet)
  }
}

@Tag("features")
class PluginFeatureMap @JvmOverloads constructor(initialFeatureMap: Map<String, Set<PluginData>> = emptyMap()) : ModificationTracker {
  @JvmField
  @XMap
  val featureMap = HashMap<String, PluginDataSet>()

  @JvmField
  @Attribute
  var lastUpdateTime: Long = 0L

  private var modificationCount = 0L

  init {
    for (entry in initialFeatureMap.entries) {
      featureMap.put(entry.key, PluginDataSet(entry.value))
    }
  }

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