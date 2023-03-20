// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.concurrency

import kotlinx.coroutines.withTimeout
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.await
import kotlin.jvm.Throws
import kotlin.time.Duration

/**
 * Waits with timeout for completion of [this] promise WITH blocking a thread.
 * Note: This code doesn't pump edt events if this wait happens on it.
 * @see com.intellij.testFramework.concurrency.waitForPromiseAndPumpEdt
 */
@Throws(java.util.concurrent.TimeoutException::class)
fun <R> Promise<R>.waitForPromise(timeout: Duration): R? {
  return blockingGet(timeout.inWholeMilliseconds.toInt())
}

/**
 * Awaits with timeout for completion of [this] promise WITHOUT blocking a thread.
 * @see org.jetbrains.concurrency.await
 */
@Throws(java.util.concurrent.TimeoutException::class)
suspend fun <R> Promise<R>.awaitPromise(timeout: Duration): R {
  return withTimeout(timeout) {
    await()
  }
}
