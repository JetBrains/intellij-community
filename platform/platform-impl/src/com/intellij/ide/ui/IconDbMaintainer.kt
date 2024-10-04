// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.ui.svg.SvgCacheManager
import com.intellij.ui.svg.activeSvgCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private fun nowAsDuration() = System.currentTimeMillis().toDuration(DurationUnit.MILLISECONDS)

// icons maybe loaded before app loaded, so, SvgCacheMapper cannot be as a service
@Service(Service.Level.APP)
@Internal
class IconDbMaintainer : SettingsSavingComponent {
  // we save only once every 2 minutes, and not earlier than 2 minutes after the start
  private var lastSaved = nowAsDuration()

  override suspend fun save() {
    val exitInProgress = ApplicationManager.getApplication().isExitInProgress
    if (!exitInProgress && (nowAsDuration() - lastSaved) < 2.minutes) {
      return
    }

    val svgCache = activeSvgCache ?: return
    withContext(Dispatchers.IO) {
      svgCache.save()
      lastSaved = nowAsDuration()
    }
  }
}

private class IconCacheInvalidator : CachesInvalidator() {
  override fun invalidateCaches() {
    SvgCacheManager.invalidateCache()
  }
}