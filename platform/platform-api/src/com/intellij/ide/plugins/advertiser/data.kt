// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Comparing
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XMap

@Tag("plugin")
class PluginData(
  @Attribute("pluginId") val pluginIdString: String = "",
  @Attribute("pluginName") private val nullablePluginName: String? = null,
  @Attribute("bundled") val isBundled: Boolean = false,
  @Attribute("fromCustomRepository") val isFromCustomRepository: Boolean = false,
) : Comparable<PluginData> {

  val pluginId: PluginId = PluginId.getId(pluginIdString)

  val pluginName = nullablePluginName ?: pluginIdString

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
class FeaturePluginData(
  @Attribute("displayName") val displayName: String = "",
  @Attribute("pluginData") val pluginData: PluginData = PluginData(),
)

@Tag("plugins")
class PluginDataSet(dataSet: Set<PluginData> = setOf()) {

  @JvmField
  @OptionTag
  val dataSet = mutableSetOf<PluginData>()

  init {
    this.dataSet += dataSet
  }
}

@Tag("extensions")
class KnownExtensions(extensionsMap: Map<String, Set<PluginData>> = mapOf()) {

  @JvmField
  @OptionTag
  @XMap
  val extensionsMap = mutableMapOf<String, PluginDataSet>()

  init {
    extensionsMap.entries.forEach { entry ->
      this.extensionsMap[entry.key] = PluginDataSet(entry.value)
    }
  }

  fun find(extension: String): Set<PluginData> = extensionsMap[extension]?.dataSet ?: setOf()
}