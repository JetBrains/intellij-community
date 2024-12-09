// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.ide.RegionSettings.RegionSettingsListener
import com.intellij.ide.RegionUrlMapper
import com.intellij.internal.statistic.eventLog.EventLogInternalApplicationInfo
import com.intellij.internal.statistic.eventLog.StatisticsRegionUrlMapperService
import com.intellij.openapi.components.serviceIfCreated
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.minutes

/**
 * Service implementation to access [com.intellij.ide.RegionUrlMapper] from code in *intellij.platform.statistics* module without introducing dependency
 *
 * If changed, please, update [StatisticsRegionUrlMapperService.Companion.getInstance]
 */
@ApiStatus.Internal
private class StatisticsRegionUrlMapperServiceImpl(val scope: CoroutineScope) : StatisticsRegionUrlMapperService() {
  @Volatile
  private var url: String? = null

  /**
   * To simplify usages of [StatisticsRegionUrlMapperServiceImpl.getRegionUrl] in statistics code,
   * region-specific url is updated and cached in coroutine.
   *
   * Cached value is periodically updated to ensure it is in sync with updated values in [com.intellij.ide.RegionUrlMapper]
  */
  init {
    scope.launch {
      while (isActive) {
        url = RegionUrlMapper.tryMapUrl(EventLogInternalApplicationInfo.EVENT_LOG_SETTINGS_URL_TEMPLATE).await()
        delay(10.minutes)
      }
    }
  }

  override fun getRegionUrl(): String? = url

  fun updateUrl() {
    scope.launch {
      url = RegionUrlMapper.tryMapUrl(EventLogInternalApplicationInfo.EVENT_LOG_SETTINGS_URL_TEMPLATE).await()
    }
  }

}

private class StatisticsRegionSettingsListener : RegionSettingsListener {
  override fun regionChanged() {
    val service = serviceIfCreated<StatisticsRegionUrlMapperService>()
    if (service is StatisticsRegionUrlMapperServiceImpl) {
      service.updateUrl()
    }
  }
}
