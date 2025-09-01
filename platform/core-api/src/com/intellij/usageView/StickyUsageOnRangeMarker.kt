// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usageView

import org.jetbrains.annotations.ApiStatus

/**
 * Marker interface for usages supporting [StickyUsageInfoOnRangeMarker]
 */
@ApiStatus.Internal
interface StickyUsageOnRangeMarker {
  val stickyUsageInfos: List<StickyUsageInfoOnRangeMarker>
}