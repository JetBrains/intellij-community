// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.whatsNew

import com.intellij.ide.Region
import com.intellij.ide.RegionSettingsService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.function.Consumer

@Service(Service.Level.PROJECT)
internal class JcefWhatsNewPageOpening(private val scope: CoroutineScope) {
  @JvmOverloads
  fun openAfterRegionCheck(
    hasAirPromo: Boolean,
    openPage: Consumer<Boolean>,
    currentRegion: suspend () -> Region = { RegionSettingsService.getInstanceAsync().getCurrentRegion() },
  ) {
    if (!hasAirPromo) {
      openPage.accept(false)
      return
    }
    scope.launch {
      val region = currentRegion()
      withContext(Dispatchers.EDT) {
        openPage.accept(region != Region.CHINA)
      }
    }
  }
}
