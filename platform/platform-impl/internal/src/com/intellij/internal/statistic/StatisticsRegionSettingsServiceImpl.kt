// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.ide.RegionSettings
import com.intellij.internal.statistic.eventLog.StatisticsRegionSettingsService
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger

/**
 * Service implementation to access [com.intellij.ide.RegionSettings.getRegion] from code in *intellij.platform.statistics* module without introducing dependency
 *
 * If changed, please, update [StatisticsRegionSettingsService.Companion.getInstance]
 */
private class StatisticsRegionSettingsServiceImpl : StatisticsRegionSettingsService() {
  companion object {
    val LOG = logger<StatisticsRegionSettingsServiceImpl>()
  }

  @Volatile
  var regionCodeName: String? = null

  /**
   * Statistics code region code is updated and cached.
   *
   * Cached value is updated to ensure it is in sync with updated values in [com.intellij.ide.RegionSettings.getRegion]
   */
  init {
    regionCodeName = RegionSettings.getRegion().externalName()
    LOG.info("Statistics. Region code is $regionCodeName")
  }

  override fun getRegionCode(): String? = regionCodeName

  fun updateRegionCode() {
    regionCodeName = RegionSettings.getRegion().externalName()
    LOG.info("Statistics. Region code was updated. New region code is $regionCodeName")
  }
}

private class StatisticsRegionSettingsListener : RegionSettings.RegionSettingsListener {
  override fun regionChanged() {
    val service = serviceIfCreated<StatisticsRegionSettingsService>()
    if (service is StatisticsRegionSettingsServiceImpl) {
      service.updateRegionCode()
      IntellijSensitiveDataValidator.clearInstances()
    }
  }
}
