// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.nio.file.Path

abstract class PerformanceWatcher {
  interface Snapshot {
    fun logResponsivenessSinceCreation(activityName: @NonNls String)

    fun logResponsivenessSinceCreation(activityName: @NonNls String, spanName: String?)

    fun getLogResponsivenessSinceCreationMessage(activityName: @NonNls String): String?

    fun getLogResponsivenessSinceCreationMessage(activityName: @NonNls String, spanName: String?): String?
  }

  companion object {
    const val DUMP_PREFIX: String = "threadDump-"

    private var instance: PerformanceWatcher? = null

    @Internal
    fun getInstanceIfCreated(): PerformanceWatcher? {
      return instance
    }

    @JvmStatic
    fun getInstance(): PerformanceWatcher {
      LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred()
      var instance = instance
      if (instance == null) {
        instance = ApplicationManager.getApplication().service<PerformanceWatcher>()
        PerformanceWatcher.instance = instance
      }
      return instance
    }

    init {
      ApplicationManager.registerCleaner { instance = null }
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

  protected abstract fun newSnapshot(): Snapshot

  abstract suspend fun processUnfinishedFreeze(consumer: suspend (Path, Int) -> Unit)

  abstract val dumpInterval: Int

  abstract val unresponsiveInterval: Int

  abstract val maxDumpDuration: Int

  abstract val jitProblem: String?

  abstract fun clearFreezeStacktraces()

  @Internal
  abstract fun edtEventStarted()

  @Internal
  abstract fun edtEventFinished()

  /**
   * @param stripDump if set to true, then some information in the dump that is considered useless for debugging
   * might be omitted. This should significantly reduce the size of the dump.
   *
   *
   * For example, some stack frames that correspond to `kotlinx.coroutines`
   * library internals might be omitted.
   */
  abstract fun dumpThreads(pathPrefix: String, appendMillisecondsToFileName: Boolean, stripDump: Boolean): Path?

  @Internal
  abstract fun startEdtSampling()
}
