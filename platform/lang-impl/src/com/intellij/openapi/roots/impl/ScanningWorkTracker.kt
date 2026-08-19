// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentHashMap

/**
 * A read action that a [FilesScanExecutor] worker is currently inside.
 *
 * [indicator] is the per-item wrapper that the write-action-priority protocol is supposed to cancel, so it is both the
 * evidence that cancellation did or did not happen and the handle needed to repair it.
 */
@Internal
class ScanningReadAction(
  @JvmField val thread: Thread,
  @JvmField val indicator: SensitiveProgressWrapper,
  @JvmField val startedAtNanos: Long,
) {
  override fun toString(): String = "$thread under $indicator"
}

/**
 * Knows which [FilesScanExecutor] workers currently hold a read action.
 *
 * Nothing else can answer that question: the futures are a local variable of [FilesScanExecutor.runOnAllThreads], and
 * both the per-worker and the per-item progress wrappers are created inline and discarded.
 * `CoreProgressManager.getCurrentIndicators()` returns indicators without thread attribution, and
 * `getProgressStateRepresentation()` returns the attribution only as text -- neither gives a handle to cancel.
 *
 * Holding a read action is exactly the state that blocks a write action, which is why registration covers the read
 * action rather than the whole worker. There is at most one entry per thread, since a scanning worker holds at most one
 * scan read action at a time; callers on the per-item path should resolve the service once per scan rather than per item.
 */
@Internal
@Service(Service.Level.APP)
class ScanningWorkTracker {
  private val activeReadActions = ConcurrentHashMap<Thread, ScanningReadAction>()

  fun isEmpty(): Boolean = activeReadActions.isEmpty()

  fun activeReadActions(): List<ScanningReadAction> = activeReadActions.values.toList()

  /** Runs [body] with the current thread registered as holding a scan read action under [indicator]. */
  fun <T> trackReadAction(indicator: SensitiveProgressWrapper?, body: () -> T): T {
    if (indicator == null) {
      return body()
    }
    val thread = Thread.currentThread()
    val outer = register(thread, indicator)
    try {
      return body()
    }
    finally {
      unregister(thread, outer)
    }
  }

  /** Returns the registration this one replaced, to be passed back to [unregister]. */
  fun register(thread: Thread, indicator: SensitiveProgressWrapper): ScanningReadAction? {
    return activeReadActions.put(thread, ScanningReadAction(thread, indicator, System.nanoTime()))
  }

  fun unregister(thread: Thread, outer: ScanningReadAction?) {
    if (outer == null) {
      activeReadActions.remove(thread)
    }
    else {
      activeReadActions[thread] = outer
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): ScanningWorkTracker = service()
  }
}
