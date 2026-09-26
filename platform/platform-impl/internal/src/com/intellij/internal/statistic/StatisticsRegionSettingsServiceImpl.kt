// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.ide.Region
import com.intellij.ide.RegionSettings
import com.intellij.ide.RegionSettingsService
import com.intellij.internal.statistic.eventLog.StatisticsRegionSettingsService
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.openapi.components.serviceIfCreated

/**
 * Service implementation to access [com.intellij.ide.RegionSettings.getRegion] from code in *intellij.platform.statistics* module without introducing dependency
 *
 * If changed, please, update [StatisticsRegionSettingsService.Companion.getInstance]
 */
internal class StatisticsRegionSettingsServiceImpl : StatisticsRegionSettingsService() {
  @Volatile
  private var regionCodeName: String? = null

  override fun getRegionCode(): String? {
    val current = regionCodeName
    if (current != null) return current

    val readRegion = RegionSettingsService.getInstance().getCurrentRegionBlocking().externalName()
    regionCodeName = readRegion
    return readRegion
  }

  fun updateRegionCode(region: Region) {
    regionCodeName = region.externalName()
  }
}

private class StatisticsRegionSettingsListener : RegionSettings.RegionSettingsListener {
  override fun regionChanged() {
    val service = serviceIfCreated<StatisticsRegionSettingsService>()
    if (service is StatisticsRegionSettingsServiceImpl) {
      service.updateRegionCode(RegionSettingsService.getInstance().getCurrentRegionIfKnown() ?: Region.NOT_SET)
      IntellijSensitiveDataValidator.clearInstances()
    }
  }
}
