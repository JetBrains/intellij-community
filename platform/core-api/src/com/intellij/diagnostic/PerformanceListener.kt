// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

@Internal
@Experimental
interface PerformanceListener {
  fun uiFreezeStarted(reportDir: Path, coroutineScope: CoroutineScope) {
  }

  /**
   * Invoked after the UI has become responsive again following a [.uiFreezeStarted] event.
   *
   * @param durationMs freeze duration in milliseconds
   * @param reportDir  folder where all freeze report data is collected (maybe temporary,
   * the final folder will be provided in [.uiFreezeRecorded])
   */
  fun uiFreezeFinished(durationMs: Long, reportDir: Path?) {}

  /**
   * Invoked after the UI has become responsive again and all data is saved into the final report folder location
   *
   * @param durationMs freeze duration in milliseconds
   * @param reportDir  folder where all freeze report data is collected
   */
  fun uiFreezeRecorded(durationMs: Long, reportDir: Path?) {}

  // used in 3rd party plugin
  @Deprecated("Use uiResponded(latencyMs: Long, wasFreezePopupShown: Boolean)", ReplaceWith("uiResponded(latencyMs, false)"))
  fun uiResponded(latencyMs: Long) {}

  /**
   * Invoked on each UI response sampled every `performance.watcher.sampling.interval.ms` set in the Registry.
   * Executed not in EDT.
   */
  fun uiResponded(uiLagData: UiLagData) {
    @Suppress("DEPRECATION")
    uiResponded(uiLagData.latencyMs)
  }

  data class UiLagData(
    /**
     * Time between scheduling a UI event and executing it, in milliseconds
     */
    val latencyMs: Long,
    /**
     * Whether the freeze popup ('$ProductName' is not responding) was shown during the UI response.
     * The appearance of this popup means that the UI was frozen due to processing of the Read-Write lock.
     */
    val wasFreezePopupShown: Boolean,
    )

  /**
   * Invoked after thread state has been dumped to a file.
   */
  fun dumpedThreads(toFile: Path, dump: ThreadDump) {}
}