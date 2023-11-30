// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.features

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor

class PluginManagerSearchResultFeatureProvider {
  companion object {
    private val NAME_LENGTH_DATA_KEY = EventFields.Int("nameLength")
    private val DEVELOPED_BY_JETBRAINS_DATA_KEY = EventFields.Boolean("byJetBrains")

    fun getFeaturesDefinition(): List<EventField<*>> {
      return arrayListOf(
        NAME_LENGTH_DATA_KEY, DEVELOPED_BY_JETBRAINS_DATA_KEY
      )
    }
  }

  fun getSearchStateFeatures(userQuery: String?, descriptor: IdeaPluginDescriptor): List<EventPair<*>> = buildList {
    val pluginInfo = getPluginInfoByDescriptor(descriptor)

    add(NAME_LENGTH_DATA_KEY.with(descriptor.name.length))
    add(DEVELOPED_BY_JETBRAINS_DATA_KEY.with(pluginInfo.isDevelopedByJetBrains()))
  }
}