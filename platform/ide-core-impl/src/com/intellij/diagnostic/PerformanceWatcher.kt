// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.CachedSingletonsRegistry
import com.intellij.openapi.components.service
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.nio.file.Path

abstract class PerformanceWatcher : Disposable {
  interface Snapshot {
    fun logResponsivenessSinceCreation(activityName: @NonNls String)

    fun getLogResponsivenessSinceCreationMessage(activityName: @NonNls String): String?
  }

  companion object {
    const val DUMP_PREFIX = "threadDump-"

    private val instance = CachedSingletonsRegistry.lazy {
      ApplicationManager.getApplication().service<PerformanceWatcher>()
    }

    @ApiStatus.Internal
    fun getInstanceOrNull(): PerformanceWatcher? {
      return if (LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred) instance.get() else null
    }

    @JvmStatic
    fun getInstance(): PerformanceWatcher {
      LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred()
      return instance.get()
    }

    @JvmStatic
    fun takeSnapshot(): Snapshot = getInstance().newSnapshot()

    @JvmStatic
    fun printStacktrace(headerMsg: String, thread: Thread, stackTrace: Array<StackTraceElement>): String {
      val trace = StringBuilder(
        """$headerMsg$thread (${if (thread.isAlive) "alive" else "dead"}) ${thread.state}
--- its stacktrace:
""")
      for (stackTraceElement in stackTrace) {
        trace.append(" at ").append(stackTraceElement).append("\n")
      }
      trace.append("---\n")
      return trace.toString()
    }

    @JvmStatic
    fun dumpThreadsToConsole(message: @NonNls String?) {
      System.err.println(message)
      System.err.println(ThreadDumper.dumpThreadsToString())
    }
  }

  abstract fun scheduleWithFixedDelay(task: Runnable, delayInMs: Long): Job

  protected abstract fun newSnapshot(): Snapshot

  abstract suspend fun processUnfinishedFreeze(consumer: suspend (Path, Int) -> Unit)

  abstract val dumpInterval: Int

  abstract val unresponsiveInterval: Int

  abstract val maxDumpDuration: Int

  abstract val jitProblem: String?

  abstract fun clearFreezeStacktraces()

  @ApiStatus.Internal
  abstract fun edtEventStarted()

  @ApiStatus.Internal
  abstract fun edtEventFinished()

  @Deprecated("use {@link #dumpThreads(String, boolean, boolean)} instead")
  fun dumpThreads(pathPrefix: String, appendMillisecondsToFileName: Boolean): Path? {
    return dumpThreads(pathPrefix, appendMillisecondsToFileName, false)
  }

  /**
   * @param stripDump if set to true, then some information in the dump that is considered useless for debugging
   * might be omitted. This should significantly reduce the size of the dump.
   *
   *
   * For example, some stackframes that correspond to `kotlinx.coroutines`
   * library internals might be omitted.
   */
  abstract fun dumpThreads(pathPrefix: String, appendMillisecondsToFileName: Boolean, stripDump: Boolean): Path?
}
