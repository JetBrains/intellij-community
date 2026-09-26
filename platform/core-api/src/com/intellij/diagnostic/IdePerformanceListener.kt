// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface IdePerformanceListener {
  companion object {
    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<IdePerformanceListener> = Topic(IdePerformanceListener::class.java, Topic.BroadcastDirection.NONE, true)
  }

  /**
   * Invoked after thread state has been dumped to a file.
   */
  fun dumpedThreads(toFile: Path, dump: ThreadDump) {}

  /**
   * Invoked when IDE has detected that the UI hasn't responded for some time (5 seconds by default)
   *
   * @param reportDir folder where all freeze report data is collected (maybe temporary,
   * the final folder will be provided in [.uiFreezeRecorded])
   */
  fun uiFreezeStarted(reportDir: Path) {}

  /**
   * Invoked after the UI has become responsive again following a [.uiFreezeStarted] event.
   *
   * @param durationMs freeze duration in milliseconds
   * @param reportDir  folder where all freeze report data is collected (maybe temporary,
   * the final folder will be provided in [.uiFreezeRecorded])
   */
  fun uiFreezeFinished(durationMs: Long, reportDir: Path?) {}
}
