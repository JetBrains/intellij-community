// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.util.Alarm.ThreadToUse
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration

@ApiStatus.Internal
class SingleEdtTaskScheduler private constructor() {
  companion object {
    @JvmStatic
    fun createSingleEdtTaskScheduler(): SingleEdtTaskScheduler = SingleEdtTaskScheduler()
  }

  @Suppress("UsagesOfObsoleteApi")
  private val impl = SingleAlarm(
    task = {},
    delay = 0,
    parentDisposable = null,
    threadToUse = ThreadToUse.SWING_THREAD,
    coroutineScope = null,
  )

  val isEmpty: Boolean
    get() = impl.isEmpty

  val isDisposed: Boolean
    get() = impl.isDisposed

  /**
   * The same as for [SingleAlarm], if you call [request] several times, we throttle but not debounce.
   */
  fun request(delay: Long, task: Runnable) {
    val modalityState = ModalityState.defaultModalityState().takeIf { it !== ModalityState.nonModal() }?.asContextElement()
    impl.scheduleTask(delay = delay, customModality = modalityState, cancelCurrent = false, task = { task.run() })
  }

  fun request(delay: Long, modality: ModalityState, task: Runnable) {
    impl.scheduleTask(delay = delay, customModality = modality.asContextElement(), cancelCurrent = false, task = { task.run() })
  }

  /**
   * Use it if you want `debounce` behavior instead of `throttle` (see [request]).
   */
  fun cancelAndRequest(delay: Long, task: Runnable) {
    val modalityState = ModalityState.defaultModalityState().takeIf { it !== ModalityState.nonModal() }?.asContextElement()
    impl.scheduleTask(delay = delay, customModality = modalityState, task = { task.run() })
  }

  fun cancelAndRequest(delay: Long, modality: ModalityState, task: Runnable) {
    impl.scheduleTask(delay = delay, customModality = modality.asContextElement(), task = { task.run() })
  }

  fun cancel() {
    impl.cancel()
  }

  fun dispose() {
    @Suppress("SSBasedInspection")
    impl.dispose()
  }

  @TestOnly
  @RequiresEdt
  fun waitForAllExecuted(timeout: Duration) {
    impl.waitForAllExecutedInEdt(timeout)
  }
}