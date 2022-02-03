// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState

class SingleAlarm @JvmOverloads constructor(
  private val task: Runnable,
  private val delay: Int,
  private val parentDisposable: Disposable?,
  private val threadToUse: ThreadToUse = ThreadToUse.SWING_THREAD,
  private val modalityState: ModalityState? = computeDefaultModality(threadToUse)
) : Alarm(threadToUse, parentDisposable) {
  constructor(task: Runnable, delay: Int, modalityState: ModalityState, parentDisposable: Disposable)
    : this(task, delay = delay, parentDisposable = parentDisposable, threadToUse = ThreadToUse.SWING_THREAD, modalityState = modalityState)

  constructor(task: Runnable, delay: Int, threadToUse: ThreadToUse, parentDisposable: Disposable)
    : this(task, delay = delay, parentDisposable = parentDisposable, threadToUse = threadToUse, modalityState = computeDefaultModality(threadToUse))

  constructor(task: Runnable, delay: Int) : this(task, delay, null)

  init {
    if (threadToUse == ThreadToUse.SWING_THREAD && modalityState == null) {
      throw IllegalArgumentException("modalityState must be not null if threadToUse == ThreadToUse.SWING_THREAD")
    }
  }

  @JvmOverloads
  fun request(forceRun: Boolean = false, delay: Int = this@SingleAlarm.delay) {
    if (isEmpty) {
      _addRequest(task, if (forceRun) 0 else delay.toLong(), modalityState)
    }
  }

  fun request(modalityState: ModalityState) {
    if (isEmpty) {
      _addRequest(task, delay.toLong(), modalityState)
    }
  }

  /**
   * Cancel doesn't interrupt already running task.
   */
  fun cancel() {
    cancelAllRequests()
  }

  /**
   * Cancel doesn't interrupt already running task.
   */
  @JvmOverloads
  fun cancelAndRequest(forceRun: Boolean = false) {
    if (!isDisposed) {
      cancelAllAndAddRequest(task, if (forceRun) 0 else delay, modalityState)
    }
  }

  fun getUnfinishedRequest(): Runnable? {
    val unfinishedTasks = unfinishedRequests
    if (unfinishedTasks.isEmpty()) {
      return null
    }

    LOG.assertTrue(unfinishedTasks.size == 1)
    return unfinishedTasks.first()
  }

  companion object {
    private fun computeDefaultModality(threadToUse: ThreadToUse): ModalityState? {
      return when (threadToUse) {
        ThreadToUse.SWING_THREAD -> ModalityState.NON_MODAL
        else -> null
      }
    }

    fun pooledThreadSingleAlarm(delay: Int, parentDisposable: Disposable, task: () -> Unit): SingleAlarm {
      return SingleAlarm(Runnable(task), delay = delay, threadToUse = ThreadToUse.POOLED_THREAD, parentDisposable = parentDisposable)
    }
  }
}
