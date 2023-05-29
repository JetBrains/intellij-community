// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.sun.management.OperatingSystemMXBean
import kotlinx.coroutines.*
import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds

internal open class SamplingTask(@JvmField internal val dumpInterval: Int, maxDurationMs: Int, coroutineScope: CoroutineScope) {
  private val maxDumps: Int
  private val myThreadInfos = ArrayList<Array<ThreadInfo>>()
  private val job: Job?
  private val startTime: Long
  private var currentTime: Long
  private val gcStartTime: Long
  private var gcCurrentTime: Long
  val processCpuLoad: Double

  val threadInfos: List<Array<ThreadInfo>>
    get() = myThreadInfos
  val totalTime: Long
    get() = TimeUnit.NANOSECONDS.toMillis(currentTime - startTime)
  val gcTime: Long
    get() = gcCurrentTime - gcStartTime

  init {
    maxDumps = maxDurationMs / dumpInterval
    startTime = System.nanoTime()
    currentTime = startTime
    gcStartTime = currentGcTime()
    gcCurrentTime = gcStartTime
    processCpuLoad = (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).processCpuLoad

    job = coroutineScope.launch {
      val delayDuration = dumpInterval.milliseconds
      while (true) {
        dumpThreads()
        delay(delayDuration)
      }
    }
  }

  private suspend fun dumpThreads() {
    currentTime = System.nanoTime()
    gcCurrentTime = currentGcTime()
    val infos = ThreadDumper.getThreadInfos(THREAD_MX_BEAN, false)
    coroutineContext.ensureActive()
    myThreadInfos.add(infos)
    if (myThreadInfos.size >= maxDumps) {
      stop()
    }

    coroutineContext.ensureActive()
    dumpedThreads(ThreadDumper.getThreadDumpInfo(infos, true))
  }

  protected open suspend fun dumpedThreads(threadDump: ThreadDump) {}

  fun isValid(dumpingDuration: Long): Boolean {
    return myThreadInfos.size >= 10L.coerceAtLeast(maxDumps.toLong().coerceAtMost(dumpingDuration / dumpInterval / 2))
  }

  open fun stop() {
    job?.cancel()
  }
}

private val THREAD_MX_BEAN = ManagementFactory.getThreadMXBean()
private val GC_MX_BEANS = ManagementFactory.getGarbageCollectorMXBeans()

private fun currentGcTime(): Long = GC_MX_BEANS.sumOf { it.collectionTime }