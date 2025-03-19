// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.containers.FList
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

@Service
@ApiStatus.Internal
class ThreadDumpService(private val cs: CoroutineScope) {

  fun start(initialDelayMillis: Int, dumpIntervalMillis: Int, maxTraces: Int, specificThread: Thread?): Cookie {
    val specificThreadTraces = AtomicReference<FList<Throwable>>(FList.emptyList())
    val activityStartNs = System.nanoTime()

    val job = cs.launch {
      val isEDT = EDT.getEventDispatchThread() == specificThread
      val prefix = "${if (isEDT) "EDT" else "BGT"}-trace-at-"
      var traces = FList.emptyList<Throwable>()
      delay(initialDelayMillis.toLong())
      repeat(maxTraces) {
        val iterationStartTimeNs = System.nanoTime()

        if (specificThread != null) {
          val throwable = Throwable("$prefix${(System.nanoTime() - activityStartNs) / 1000000}-ms (${traces.size + 1}/$maxTraces)")
          throwable.stackTrace = specificThread.stackTrace
          traces = traces.prepend(throwable)
          specificThreadTraces.set(traces)
        }

        val elapsedMs = (System.nanoTime() - iterationStartTimeNs) / 1000000
        // try to be as close as dumpIntervalMillis
        delay(max(0L, dumpIntervalMillis - elapsedMs))
      }
    }

    return object : Cookie {
      override val startNanos: Long
        get() = activityStartNs

      var traces0: List<Throwable>? = null
      override val traces: List<Throwable>
        get() = traces0 ?: throw IllegalStateException("not closed yet")

      override fun close() {
        traces0 = specificThreadTraces.get()
        job.cancel("close")
      }
    }
  }

  interface Cookie : AutoCloseable {
    val startNanos: Long
    val traces: List<Throwable>
    override fun close()
  }

  companion object {
    @JvmStatic
    fun getInstance(): ThreadDumpService = service<ThreadDumpService>()
  }
}
