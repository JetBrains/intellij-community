// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.openapi.progress.assertRunBlockingBackgroundThreadAndNoWriteAction
import com.intellij.util.IntelliJCoroutinesFacade
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

internal fun waitWithParallelismCompensation(runnable: Runnable) {
  IntelliJCoroutinesFacade.runAndCompensateParallelism(500.milliseconds, runnable::run)
}

/**
 * Blocking version of [com.intellij.util.io.computeDetached].
 */
@RequiresBlockingContext
@RequiresBackgroundThread(generateAssertion = false)
@ApiStatus.Experimental
fun <T> runWithCheckCanceled(
  context: CoroutineContext = EmptyCoroutineContext,
  action: suspend CoroutineScope.() -> T,
): T {
  assertRunBlockingBackgroundThreadAndNoWriteAction()

  @OptIn(DelicateCoroutinesApi::class)
  val future = GlobalScope.async(blockingDispatcher + context, block = action).asCompletableFuture()
  try {
    return future.awaitWithCheckCanceled()
  }
  catch (e: CancellationException) {
    future.cancel(false)
    throw e
  }
}

@RequiresBlockingContext
@RequiresBackgroundThread(generateAssertion = false)
@ApiStatus.Experimental
fun <T> awaitWithCheckCanceled(deferred: Deferred<T>): T {
  assertRunBlockingBackgroundThreadAndNoWriteAction()
  return deferred.asCompletableFuture().awaitWithCheckCanceled()
}
