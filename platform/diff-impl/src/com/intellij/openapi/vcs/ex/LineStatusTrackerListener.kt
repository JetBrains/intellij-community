// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * Listener for [LineStatusTrackerBase]'s state
 */
@ApiStatus.Internal
interface LineStatusTrackerListener : EventListener {
  /**
   * Fired when [LineStatusTrackerBase.isOperational] is changed
   */
  fun onOperationalStatusChange() {}

  /**
   * Fired when [LineStatusTrackerBase.getRanges] is changed
   */
  fun onRangesChanged() {}
}