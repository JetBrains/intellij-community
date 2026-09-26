// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers.actions

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger

@ApiStatus.Internal
object WebPreviewState {
  private val previewsOpened = AtomicInteger()

  @JvmStatic
  val isPreviewOpened: Boolean
    get() = previewsOpened.get() > 0

  @JvmStatic
  fun previewOpened() {
    previewsOpened.incrementAndGet()
  }

  @JvmStatic
  fun previewClosed() {
    previewsOpened.decrementAndGet()
  }
}
