// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ConstPropertyName")

package com.intellij.platform.diagnostic.startUpPerformanceReporter

import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.ThreadDumper
import com.intellij.diagnostic.ThreadNameManager
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap

private const val pooledPrefix = "ApplicationImpl pooled thread "
private val regex = Regex(" *@[$:.A-Za-z0-9@ ]+#\\d+$")

internal class IdeThreadNameManager : ThreadNameManager {
  // ConcurrencyUtil.runUnderThreadName is used in our code (to make thread dumps clearer) and changes thread name,
  // so, use first thread name that associated with thread and not the subsequent one
  private val idToName = Long2ObjectOpenHashMap<String>()

  override fun getThreadName(event: ActivityImpl): String {
    var result = idToName.get(event.threadId)
    if (result != null) {
      return result
    }

    var name = event.threadName
    if (name.endsWith(']') && name.contains(pooledPrefix)) {
      val lastOpen = name.lastIndexOf('[')
      if (lastOpen > 0) {
        name = name.substring(lastOpen + 1, name.length - 1)
      }
    }

    result = when {
      name.startsWith("JobScheduler FJ pool ") -> name.replace("JobScheduler FJ pool ", "fj ")
      name.startsWith("ForkJoinPool.commonPool-worker-") -> name.replace("ForkJoinPool.commonPool-worker-", "fj ")
      name.startsWith("DefaultDispatcher-worker-") -> name.replace("DefaultDispatcher-worker-", "d ")
      ThreadDumper.isEDT(name) -> "edt"
      name.startsWith("Idea Main Thread") -> "idea main"
      name.startsWith(pooledPrefix) -> name.replace(pooledPrefix, "pooled ")
      name.startsWith("StatisticsFileEventLogger: ") -> "StatFileEventLogger"
      else -> name
    }

    //main @coroutine#4946
    result = result.replace(regex, "")
    idToName.put(event.threadId, result)
    return result
  }
}