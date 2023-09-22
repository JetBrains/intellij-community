package com.intellij.execution.multilaunch.design

import com.jetbrains.rd.util.concurrentMapOf
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class Debouncer(
  private val delay: Long,
  private val timeUnit: TimeUnit
) {
  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private val delayedMap = concurrentMapOf<Any, Future<*>>()

  fun call(key: Any, callback: () -> Unit) {
    val previous = delayedMap.put(key, scheduler.schedule(Runnable {
      callback()
    }, delay, timeUnit))
    previous?.cancel(true)
  }

  fun cancel(key: Any) {
    val added = delayedMap.remove(key)
    added?.cancel(true)
  }
}