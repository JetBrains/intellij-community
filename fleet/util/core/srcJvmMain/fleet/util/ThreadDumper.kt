// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo
import java.lang.management.ThreadMXBean
import kotlin.math.min

object ThreadDumper {
  fun dumpThreadsToString(): String {
    val writer = StringWriter()
    dumpThreadInfos(threadInfos, writer)
    return writer.toString()
  }

  val threadInfos: Array<ThreadInfo?>
    get() = getThreadInfos(ManagementFactory.getThreadMXBean(), sort = true)

  fun getThreadInfos(threadMXBean: ThreadMXBean, sort: Boolean): Array<ThreadInfo?> {
    var threads: Array<ThreadInfo?>
    try {
      threads = threadMXBean.dumpAllThreads(false, false)
    }
    catch (_: Exception) {
      threads = threadMXBean.getThreadInfo(threadMXBean.allThreadIds, Int.MAX_VALUE)
    }
    var o = 0
    for (i in threads.indices) {
      val info: ThreadInfo? = threads[i]
      if (info != null) {
        threads[o++] = info
      }
    }
    threads = when {
      threads.size == o -> threads
      o == 0 -> arrayOfNulls(0)
      else -> {
        threads.copyInto(arrayOfNulls<ThreadInfo?>(o), endIndex = min(threads.size, o))
      }
    }
    if (sort) {
      sort(threads)
    }
    return threads
  }

  private fun isEDT(threadName: String?): Boolean {
    return threadName != null && (threadName.startsWith("AWT-EventQueue") || threadName.contains("AppKit"))
  }

  private fun dumpThreadInfos(threadInfo: Array<ThreadInfo?>, f: Writer): Array<StackTraceElement?>? {
    var edtStack: Array<StackTraceElement?>? = null
    for (info in threadInfo) {
      if (info != null) {
        val name = info.threadName
        val stackTrace = info.stackTrace
        if (edtStack == null && isEDT(name)) {
          edtStack = stackTrace
        }
        if (isIdleDefaultCoroutineDispatch(name, stackTrace)) {
          // avoid 64 coroutine dispatch idle threads littering thread dump
          continue
        }
        dumpCallStack(info, f, stackTrace)
      }
    }
    return edtStack
  }

  private fun isIdleDefaultCoroutineDispatch(name: String?, stackTrace: Array<StackTraceElement?>): Boolean {
    return name != null && name.startsWith("DefaultDispatcher-worker-")
           && stackTrace.size == 6 && stackTrace[0]!!.isNativeMethod && stackTrace[0]!!.methodName == "park" && stackTrace[0]!!.className == "jdk.internal.misc.Unsafe"
           && stackTrace[1]!!.methodName == "parkNanos" && stackTrace[1]!!.className == "java.util.concurrent.locks.LockSupport"
           && stackTrace[2]!!.methodName == "park" && stackTrace[2]!!.className == "kotlinx.coroutines.scheduling.CoroutineScheduler\$Worker"
           && stackTrace[3]!!.methodName == "tryPark" && stackTrace[3]!!.className == "kotlinx.coroutines.scheduling.CoroutineScheduler\$Worker"
           && stackTrace[4]!!.methodName == "runWorker" && stackTrace[4]!!.className == "kotlinx.coroutines.scheduling.CoroutineScheduler\$Worker"
           && stackTrace[5]!!.methodName == "run" && stackTrace[5]!!.className == "kotlinx.coroutines.scheduling.CoroutineScheduler\$Worker"
  }

  fun sort(threads: Array<ThreadInfo?>): Array<ThreadInfo> {
    return threads.filterNotNull().sortedWith(compareBy<ThreadInfo> { threadInfo ->
      !isEDT(threadInfo.threadName)
    }.thenComparing { threadInfo ->
      threadInfo.threadState != Thread.State.RUNNABLE
    }.thenComparing { threadInfo ->
      threadInfo.stackTrace?.size?.unaryMinus() ?: 0
    }.thenComparing { threadInfo ->
      threadInfo.threadName.orEmpty()
    }).toTypedArray()
  }

  private fun dumpCallStack(info: ThreadInfo, f: Writer, stackTraceElements: Array<StackTraceElement?>) {
    try {
      val s = buildString {
        append("\"")
        append(info.threadName)
        append("\"")
        append(" prio=0 tid=0x0 nid=0x0 ").append(getReadableState(info.threadState)).append("\n")
        append("     java.lang.Thread.State: ").append(info.threadState).append("\n")
        if (info.lockName != null) {
          append(" on ").append(info.lockName)
        }
        if (info.lockOwnerName != null) {
          append(" owned by \"").append(info.lockOwnerName).append("\" Id=").append(info.lockOwnerId)
        }
        if (info.isSuspended) {
          append(" (suspended)")
        }
        if (info.isInNative) {
          append(" (in native)")
        }
      }

      f.write(s + "\n")
      printStackTrace(f, stackTraceElements)
      f.write("\n")
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  private fun printStackTrace(f: Writer, stackTraceElements: Array<StackTraceElement?>) {
    try {
      for (element in stackTraceElements) {
        f.write("\tat $element\n")
      }
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  private fun getReadableState(state: Thread.State): String? {
    return when (state) {
      Thread.State.BLOCKED -> "blocked"
      Thread.State.TIMED_WAITING, Thread.State.WAITING -> "waiting on condition"
      Thread.State.RUNNABLE -> "runnable"
      Thread.State.NEW -> "new"
      Thread.State.TERMINATED -> "terminated"
    }
  }
}