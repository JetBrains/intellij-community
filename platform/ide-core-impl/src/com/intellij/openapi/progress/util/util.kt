// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.util.IntelliJCoroutinesFacade
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
internal fun waitWithParallelismCompensation(runnable: Runnable) {
  @OptIn(InternalCoroutinesApi::class)
  IntelliJCoroutinesFacade.runAndCompensateParallelism(500.milliseconds, runnable::run)
}

/**
 * Blocking version of [com.intellij.util.io.computeDetached].
 */
@ApiStatus.Experimental
@DelicateCoroutinesApi
fun <T> runWithCheckCanceled(
  context: CoroutineContext = EmptyCoroutineContext,
  action: suspend CoroutineScope.() -> T,
): T {
  val future = GlobalScope.async(blockingDispatcher + context, block = action).asCompletableFuture()
  try {
    return ProgressIndicatorUtils.awaitWithCheckCanceled(future)
  } catch (e: CancellationException) {
    future.cancel(false)
    throw e
  }
}