// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.SVGLoader
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

// icons maybe loaded before app loaded, so, SvgCacheMapper cannot be as a service
private class IconDbMaintainer : Disposable {
  init {
    @Suppress("DEPRECATION")
    ApplicationManager.getApplication().coroutineScope.launch(CoroutineName("save SVG icon cache")) {
      delay(5.minutes)
      SVGLoader.persistentCache?.save()
    }
  }

  override fun dispose() {
    SVGLoader.persistentCache?.close()
  }
}