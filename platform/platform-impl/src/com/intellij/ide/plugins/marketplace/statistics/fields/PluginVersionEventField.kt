// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.fields

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.utils.PluginInfo


internal data class PluginVersionEventField(override val name: String) : PrimitiveEventField<Pair<PluginInfo, String?>>() {
  override val validationRule: List<String>
    get() = listOf("{util#plugin_version}")

  override fun addData(fuData: FeatureUsageData, value: Pair<PluginInfo, String?>) {
    if (!value.second.isNullOrEmpty() && value.first.isSafeToReport()) {
      fuData.addData(name, value.second!!)
    }
  }
}