// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.ui.svg.SvgCacheManager
import com.intellij.ui.svg.activeSvgCache
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.minutes

// icons maybe loaded before app loaded, so, SvgCacheMapper cannot be as a service
private class IconDbMaintainer : ApplicationInitializedListener {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(asyncScope: CoroutineScope) {
    asyncScope.launch(CoroutineName("auto-save icon cache")) {
      try {
        delay(5.minutes)

        val svgCache = activeSvgCache ?: return@launch
        while (true) {
          delay(5.minutes)
          withContext(Dispatchers.IO) {
            runCatching {
              svgCache.save()
            }.getOrLogException(thisLogger())
          }
        }
      }
      finally {
        val svgCache = activeSvgCache
        if (svgCache != null) {
          withContext(NonCancellable + Dispatchers.IO) {
            runCatching {
              svgCache.close()
            }.getOrLogException(thisLogger())
          }
        }
      }
    }
  }
}

private class IconCacheInvalidator : CachesInvalidator() {
  override fun invalidateCaches() {
    SvgCacheManager.invalidateCache()
  }
}