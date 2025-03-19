// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * Listener for [LineStatusTrackerI]'s state
 */
@ApiStatus.Internal
interface LineStatusTrackerListener : EventListener {
  fun onBecomingValid() {}

  /**
   * Fired when lines at [LineStatusTrackerI.getRanges] are changed
   */
  fun onRangesChanged() {}
}