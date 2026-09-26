// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.portsWatcher

import com.intellij.execution.portsWatcher.ProcessPortsWatcher.Companion.startWatching
import com.intellij.execution.portsWatcher.impl.ProcessPortsWatcherImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.LocalEelDescriptor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Watches a process tree for child processes that start listening on TCP ports and reports changes
 * to a [ListeningPortHandler].
 *
 * Instances are obtained via [startWatching] method.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProcessPortsWatcher {
  /**
   * Hints the watcher to shorten its next polling delay. Safe to call from any thread; calls coalesce.
   */
  fun resetDelay()

  companion object {
    private val LOG = logger<ProcessPortsWatcher>()

    /**
     * Creates a watcher for processes that listen on TCP ports inside the environment described by
     * [eelDescriptor] and starts the background poll loop immediately on [coroutineScope].
     *
     * The watcher stops when [coroutineScope] is canceled.
     *
     * If [eelDescriptor] is a non-local Windows target, this method returns a no-op watcher without
     * starting any polling: port detection on remote Windows is not supported.
     *
     * @param eelDescriptor environment where [pid] is running.
     * @param pid ID of the process to watch for. **PID must relate to the process running in [eelDescriptor] environment**
     * @param handler receives port state change events.
     * @param coroutineScope governs the lifetime of the poll loop.
     */
    fun startWatching(
      eelDescriptor: EelDescriptor,
      pid: Long,
      handler: ListeningPortHandler,
      coroutineScope: CoroutineScope,
      options: PortListeningOptions = PortListeningOptions.INCLUDE_SELF_AND_CHILDREN,
    ): ProcessPortsWatcher {
      if (eelDescriptor.osFamily == EelOsFamily.Windows && eelDescriptor !is LocalEelDescriptor) {
        LOG.warn("Non-local Windows target ($eelDescriptor) is not supported; port detection disabled")
        return NoOpProcessPortsWatcher
      }
      return ProcessPortsWatcherImpl(eelDescriptor, pid, handler, options).also {
        it.startWatch(coroutineScope)
      }
    }
  }
}

private object NoOpProcessPortsWatcher : ProcessPortsWatcher {
  override fun resetDelay() {
    /* no-op */
  }
}