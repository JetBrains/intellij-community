// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.util.containers.UList
import com.sun.management.OperatingSystemMXBean
import kotlinx.coroutines.*
import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds

internal open class SamplingTask(@JvmField internal val dumpInterval: Int, maxDurationMs: Int, coroutineScope: CoroutineScope) {
  private val maxDumps: Int = maxDurationMs / dumpInterval

  var threadInfos: UList<Array<ThreadInfo>> = UList()
    private set

  private val job: Job?
  private val startTime: Long = System.nanoTime()
  private var currentTime: Long = startTime
  private val gcStartTime: Long = currentGcTime()
  private var gcCurrentTime: Long = gcStartTime
  val processCpuLoad: Double = (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).processCpuLoad

  val totalTime: Long
    get() = TimeUnit.NANOSECONDS.toMillis(currentTime - startTime)
  val gcTime: Long
    get() = gcCurrentTime - gcStartTime

  init {
    job = coroutineScope.launch {
      val delayDuration = dumpInterval.milliseconds
      while (true) {
        dumpThreads(asyncCoroutineScope = coroutineScope)
        delay(delayDuration)
      }
    }
  }

  private suspend fun dumpThreads(asyncCoroutineScope: CoroutineScope) {
    currentTime = System.nanoTime()
    gcCurrentTime = currentGcTime()
    val infos = ThreadDumper.getThreadInfos(THREAD_MX_BEAN, false)
    coroutineContext.ensureActive()

    threadInfos = threadInfos.add(infos)
    if (threadInfos.size > maxDumps) {
      stopDumpingThreads()
      return
    }

    asyncCoroutineScope.launch {
      val rawDump = ThreadDumper.getThreadDumpInfo(infos, true)
      val dump = EventCountDumper.addEventCountersTo(rawDump)
      dumpedThreads(dump)
    }
      // don't schedule yet another dumpedThreads - wait for completion
      .join()
  }

  protected open suspend fun dumpedThreads(threadDump: ThreadDump) {}

  open fun stop() {
    job?.cancel()
  }

  open suspend fun stopDumpingThreads() {
    job?.cancelAndJoin()
  }
}

private val THREAD_MX_BEAN = ManagementFactory.getThreadMXBean()
private val GC_MX_BEANS = ManagementFactory.getGarbageCollectorMXBeans()

private fun currentGcTime(): Long = GC_MX_BEANS.sumOf { it.collectionTime }