// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.internal.statistic.eventLog.StatisticsRegionUrlMapperService
import org.jetbrains.annotations.ApiStatus

/**
 * Service implementation to access [com.intellij.ide.RegionUrlMapper] from code in *intellij.platform.statistics* module without introducing dependency
 *
 * If changed, please, update [com.intellij.internal.statistic.eventLog.StatisticsRegionUrlMapperService.Companion.getInstance]
 */
@ApiStatus.Internal
private class StatisticsRegionUrlMapperServiceImpl : StatisticsRegionUrlMapperService() {
  override fun mapUrl(url: String?): String? {
    return RegionUrlMapper.mapUrl(url)
  }
}
