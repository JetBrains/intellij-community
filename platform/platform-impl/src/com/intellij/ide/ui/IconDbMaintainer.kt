// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.SVGLoader
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.minutes

// icons maybe loaded before app loaded, so, SvgCacheMapper cannot be as a service
private class IconDbMaintainer : Disposable {
  @Suppress("DEPRECATION")
  private val coroutineScope: CoroutineScope = ApplicationManager.getApplication().coroutineScope.childScope()

  init {
    coroutineScope.launch(CoroutineName("auto-save icon cache")) {
      delay(5.minutes)
      SVGLoader.persistentCache?.save()
    }
  }

  override fun dispose() {
    try {
      SVGLoader.persistentCache?.close()
    }
    finally {
      coroutineScope.cancel()
    }
  }
}