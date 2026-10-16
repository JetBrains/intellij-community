// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val LOG = logger<IsolatedTimelineWatcher>()

/**
 * Periodically checks [Document] on its reachability and calls the passed callback if document is going to be collected
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class IsolatedTimelineWatcher(private val scope: CoroutineScope) {
  /**
   * The active watches. The removal of a watch from this set is the token that ends the watch.
   */
  private val watches: MutableSet<TimelineWatch> = ConcurrentHashMap.newKeySet()

  @Volatile
  private var closed = false

  init {
    @OptIn(AwaitCancellationAndInvoke::class)
    scope.awaitCancellationAndInvoke {
      closed = true
      for (watch in watches.toList()) {
        try {
          watch.end()
        }
        catch (e: Throwable) {
          LOG.warn("Cannot end the isolated timeline at the project close", e)
        }
      }
    }
  }

  /**
   * Calls [onEnd] once: after the garbage collector reclaims [document], or when the project scope is cancelled.
   * Call [TimelineWatch.stop] when the timeline ends for a different reason.
   */
  fun watch(document: Document, onEnd: () -> Unit): TimelineWatch {
    val reference = WeakReference(document)
    val watch = TimelineWatch(onEnd)
    watches.add(watch)
    // The coroutine must not capture the document, otherwise the document never becomes weakly reachable.
    watch.job = scope.launch {
      var pause = INITIAL_DELAY
      while (true) {
        delay(pause)
        if (reference.get() == null) {
          watch.end()
          return@launch
        }
        pause = minOf(pause * 2, MAX_DELAY)
      }
    }
    if (closed) {
      // The cancellation handler can have taken its snapshot before the add.
      watch.end()
    }
    return watch
  }

  inner class TimelineWatch internal constructor(private val onEnd: () -> Unit) {
    @Volatile
    internal var job: Job? = null

    /**
     * Stops the check and calls `onEnd`, unless [stop] already ended this watch.
     */
    fun end() {
      if (stop()) {
        onEnd()
      }
    }

    /**
     * Stops the check without `onEnd`. The timeline calls it when it retires.
     * Returns `false` when the watch already ended. Then `onEnd` owns the end of the timeline.
     */
    fun stop(): Boolean {
      if (!watches.remove(this)) {
        return false
      }
      job?.cancel()
      return true
    }
  }

  @TestOnly
  fun activeWatchCount(): Int = watches.size

  companion object {
    private val INITIAL_DELAY = 50.milliseconds
    private val MAX_DELAY = 30.seconds

    fun getInstance(project: Project): IsolatedTimelineWatcher? {
      if (project.isDisposed) {
        return null
      }
      return project.serviceOrNull<IsolatedTimelineWatcher>()
    }
  }
}
