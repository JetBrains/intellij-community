// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

@JvmOverloads
inline fun runInAllowSaveMode(isSaveAllowed: Boolean = true, task: () -> Unit) {
  val app = ApplicationManagerEx.getApplicationEx()
  if (isSaveAllowed == app.isSaveAllowed) {
    task()
    return
  }

  app.isSaveAllowed = isSaveAllowed
  try {
    task()
  }
  finally {
    app.isSaveAllowed = !isSaveAllowed
  }
}

/**
 * Execute coroutine on pooled thread. Uncaught error will be logged.
 *
 * @see com.intellij.openapi.application.Application.executeOnPooledThread
 */
@Suppress("unused") // unused receiver
val Dispatchers.ApplicationThreadPool: CoroutineDispatcher
  @ApiStatus.Experimental
  get() = ApplicationThreadPoolDispatcher

// no need to implement isDispatchNeeded - Kotlin correctly uses the same thread if coroutines executes sequentially,
// and if launch/async is used, it is correct and expected that coroutine will be dispatched to another pooled thread.
private object ApplicationThreadPoolDispatcher : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: Runnable) {
    AppExecutorUtil.getAppExecutorService().execute(block)
  }

  override fun toString() = AppExecutorUtil.getAppExecutorService().toString()
}