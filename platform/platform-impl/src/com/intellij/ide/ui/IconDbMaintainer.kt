// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.util.SVGLoader
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.minutes

// icons maybe loaded before app loaded, so, SvgCacheMapper cannot be as a service
private class IconDbMaintainer(coroutineScope: CoroutineScope) {
  init {
    coroutineScope.launch(CoroutineName("auto-save icon cache")) {
      try {
        while (true) {
          delay(5.minutes)
          SVGLoader.cache?.save()
        }
      }
      finally {
        withContext(NonCancellable) {
          SVGLoader.cache?.close()
        }
      }
    }
  }
}