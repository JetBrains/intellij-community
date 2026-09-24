// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import org.jetbrains.annotations.ApiStatus

/**
 * Schedules reindexing in dumb mode when a bulk change suggests that many files in the project have to be reindexed.
 *
 * The default implementation ([NoopDumbModeReindexingScheduler]) does nothing. The real implementation lives in the
 * `intellij.platform.lang.impl.backend` module and delegates to [FileBasedIndexProjectHandler]. This indirection keeps
 * `intellij.platform.lang.impl` (in particular [com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl]) free
 * of a direct dependency on the indexing backend implementation, which is a prerequisite for a frontend-only (IJ Light)
 * IDE where the backend module is not loaded.
 */
@ApiStatus.Internal
interface DumbModeReindexingScheduler {
  fun scheduleReindexingInDumbMode()
}

internal class NoopDumbModeReindexingScheduler : DumbModeReindexingScheduler {
  override fun scheduleReindexingInDumbMode() {
  }
}
