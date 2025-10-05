// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CancellationUtil")

package com.intellij.util.progress

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.io.await
import com.intellij.util.io.awaitFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock

private val LOG: Logger = Logger.getInstance("#com.intellij.util.progress")

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun sleepCancellable(millis: Long) {
  runBlockingCancellable {
    delay(millis)
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun Semaphore.waitForCancellable() {
  if (isUp) {
    return
  }
  runBlockingCancellable {
    awaitFor()
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun Semaphore.waitForMaybeCancellable() {
  if (isUp) {
    return
  }
  runBlockingMaybeCancellable {
    awaitFor()
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
@JvmOverloads
fun java.util.concurrent.Semaphore.acquireMaybeCancellable(permits: Int = 1) {
  while (true) {
    ProgressManager.checkCanceled()
    try {
      if (tryAcquire(permits, ConcurrencyUtil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        return
      }
    }
    catch (e: InterruptedException) {
      //This code is a modern (less intricate) version of ProgressIndicatorUtils.awaitWithCheckCanceled().
      // awaitWithCheckCanceled() doesn't throw InterruptedException, it wraps them into PCE -- and we better
      // follow the same API here for more smooth transition -- especially important since kotlin code doesn't
      // declare (checked) InterruptedException, that makes them an unexpected surprise when called from
      // java code
      throw ProcessCanceledException(e)
    }
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun Lock.lockCancellable() {
  LOG.assertTrue(isInCancellableContext())
  while (true) {
    ProgressManager.checkCanceled()
    try {
      if (tryLock(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        return
      }
    }
    catch (e: InterruptedException) {
      //This code is a modern (less intricate) version of ProgressIndicatorUtils.awaitWithCheckCanceled().
      // awaitWithCheckCanceled() doesn't throw InterruptedException, it wraps them into PCE -- and we better
      // follow the same API here for more smooth transition -- especially important since kotlin code doesn't
      // declare (checked) InterruptedException, that makes them an unexpected surprise when called from
      // java code
      throw ProcessCanceledException(e)
    }
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun <T> Lock.withLockCancellable(action: () -> T): T {
  lockCancellable()
  try {
    return action()
  }
  finally {
    unlock()
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun Lock.withLockCancellable(action: Runnable) {
  withLockCancellable(action::run)
}

@RequiresBlockingContext
@Throws(ProcessCanceledException::class)
fun Lock.lockMaybeCancellable() {
  while (true) {
    ProgressManager.checkCanceled()
    try {
      if (tryLock(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        return
      }
    }
    catch (e: InterruptedException) {
      //This code is a modern (less intricate) version of ProgressIndicatorUtils.awaitWithCheckCanceled().
      // awaitWithCheckCanceled() doesn't throw InterruptedException, it wraps them into PCE -- and we better
      // follow the same API here for more smooth transition -- especially important since kotlin code doesn't
      // declare (checked) InterruptedException, that makes them an unexpected surprise when called from
      // java code
      throw ProcessCanceledException(e)
    }
  }
}

@RequiresBlockingContext
fun <T> Lock.withLockMaybeCancellable(action: () -> T): T {
  lockMaybeCancellable()
  try {
    return action()
  }
  finally {
    unlock()
  }
}

@RequiresBlockingContext
fun Lock.withLockMaybeCancellable(action: Runnable) {
  withLockMaybeCancellable(action::run)
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun pollCancellable(waiter: () -> Boolean) {
  runBlockingCancellable {
    while (true) {
      if (runInterruptible(block = waiter)) {
        return@runBlockingCancellable
      }
      delay(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)
    }
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun <T> Future<T>.getCancellable(): T {
  return runBlockingCancellable {
    await()
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun <T> Future<T>.getMaybeCancellable(): T {
  return runBlockingMaybeCancellable {
    await()
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun <T> CompletableFuture<T>.getCancellable(): T {
  return runBlockingCancellable {
    asDeferred().await()
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun <T> CompletableFuture<T>.getMaybeCancellable(): T {
  return runBlockingMaybeCancellable {
    asDeferred().await()
  }
}
