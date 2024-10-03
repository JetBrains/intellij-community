package com.intellij.execution.multilaunch.design

import com.jetbrains.rd.util.concurrentMapOf
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class Debouncer(
  private val launchDelayMs: Long,
  private val lifetime: Lifetime
) {
  private val dispatcher = Dispatchers.IO.limitedParallelism(1)
  private val delayedMap = concurrentMapOf<Any, Job>()

  fun call(key: Any, callback: () -> Unit) {
    val previous = delayedMap.put(key, lifetime.launch(dispatcher) {
      delay(launchDelayMs)
      callback()
    })
    previous?.cancel(null)
  }

  fun cancel(key: Any) {
    val added = delayedMap.remove(key)
    added?.cancel(null)
  }
}