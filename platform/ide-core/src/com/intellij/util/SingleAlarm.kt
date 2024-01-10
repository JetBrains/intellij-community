// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState

class SingleAlarm @JvmOverloads constructor(
  private val task: Runnable,
  private val delay: Int,
  parentDisposable: Disposable?,
  threadToUse: ThreadToUse = ThreadToUse.SWING_THREAD,
  private val modalityState: ModalityState? = if (threadToUse == ThreadToUse.SWING_THREAD) ModalityState.nonModal() else null
) : Alarm(threadToUse, parentDisposable) {
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

  companion object {
    fun pooledThreadSingleAlarm(delay: Int, parentDisposable: Disposable, task: () -> Unit): SingleAlarm {
      return SingleAlarm(task = Runnable(task),
                         delay = delay,
                         threadToUse = ThreadToUse.POOLED_THREAD,
                         parentDisposable = parentDisposable)
    }
  }
}
