// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm.ThreadToUse
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Allows scheduling a single `Runnable` instance ([task]) to be executed after a specific time interval on a specific thread.
 * [request] adds a request if it's not scheduled yet, i.e., it does not delay execution of the request
 * [cancelAndRequest] cancels the current request and schedules a new one instead, i.e., it delays execution of the request
 *
 * Consider using a [kotlinx.coroutines.flow.Flow] with [kotlinx.coroutines.flow.debounce] and [kotlinx.coroutines.flow.sample] instead
 */

class SingleAlarm @JvmOverloads constructor(
  private val task: Runnable,
  private val delay: Int,
  parentDisposable: Disposable?,
  threadToUse: ThreadToUse = ThreadToUse.SWING_THREAD,
  private val modalityState: ModalityState? = if (threadToUse == ThreadToUse.SWING_THREAD) ModalityState.nonModal() else null
) : Disposable {
  private val impl = Alarm(threadToUse, parentDisposable)

  constructor(task: Runnable, delay: Int, threadToUse: ThreadToUse, parentDisposable: Disposable)
    : this(task = task,
           delay = delay,
           parentDisposable = parentDisposable,
           threadToUse = threadToUse,
           modalityState = if (threadToUse == ThreadToUse.SWING_THREAD) ModalityState.nonModal() else null)

  constructor(task: Runnable, delay: Int) : this(task = task,
                                                 delay = delay,
                                                 parentDisposable = null,
                                                 threadToUse = ThreadToUse.SWING_THREAD,
                                                 modalityState = ModalityState.nonModal())

  init {
    if (threadToUse == ThreadToUse.SWING_THREAD && modalityState == null) {
      throw IllegalArgumentException("modalityState must be not null if threadToUse == ThreadToUse.SWING_THREAD")
    }
  }

  companion object {
    fun pooledThreadSingleAlarm(delay: Int, parentDisposable: Disposable, task: () -> Unit): SingleAlarm {
      return SingleAlarm(task = Runnable(task),
                         delay = delay,
                         threadToUse = ThreadToUse.POOLED_THREAD,
                         parentDisposable = parentDisposable)
    }
  }

  override fun dispose() {
    Disposer.dispose(impl)
  }

  val isDisposed: Boolean get() = impl.isDisposed

  val isEmpty: Boolean get() = impl.isEmpty

  @TestOnly
  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun waitForAllExecuted(timeout: Long, unit: TimeUnit) {
    return impl.waitForAllExecuted(timeout, unit)
  }

  @JvmOverloads
  fun request(forceRun: Boolean = false, delay: Int = this@SingleAlarm.delay) {
    if (impl.isEmpty) {
      impl._addRequest(task, if (forceRun) 0 else delay.toLong(), modalityState)
    }
  }

  fun request(modalityState: ModalityState) {
    if (impl.isEmpty) {
      impl._addRequest(task, delay.toLong(), modalityState)
    }
  }

  /**
   * Cancel doesn't interrupt already running task.
   */
  fun cancel() {
    impl.cancelAllRequests()
  }

  /**
   * Cancel doesn't interrupt already running task.
   */
  @JvmOverloads
  fun cancelAndRequest(forceRun: Boolean = false) {
    if (!impl.isDisposed) {
      impl.cancelAllAndAddRequest(task, if (forceRun) 0 else delay, modalityState)
    }
  }

  fun cancelAllRequests(): Int {
    return impl.cancelAllRequests()
  }
}
