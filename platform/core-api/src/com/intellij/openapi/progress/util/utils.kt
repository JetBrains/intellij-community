// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProgressIndicatorUtilsCore")
package com.intellij.openapi.progress.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.ExceptionUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.Lock

private const val MAX_REJECTED_EXECUTIONS_BEFORE_CANCELLATION = 16

@Suppress("SSBasedInspection")
private val LOG: Logger = Logger.getInstance("#com.intellij.openapi.progress.util.ProgressIndicatorUtilsCore")

fun <T> Future<T>.awaitWithCheckCanceled(): T {
  @Suppress("UsagesOfObsoleteApi")
  val indicator = ProgressManager.getInstance().getProgressIndicator()
  return awaitWithCheckCanceled(indicator)
}

fun <T> Future<T>.awaitWithCheckCanceled(indicator: ProgressIndicator?): T {
  var rejectedExecutions = 0
  while (true) {
    if (!isDone) { //short-circuit all the ProgressIndicator processing if future is already done
      checkCancelledEvenWithPCEDisabled(indicator)
    }
    try {
      return get(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }
    catch (_: TimeoutException) { }
    //BEWARE RC: in a non-cancellable section we _could_ still (re-)throw a (P)CE if the _awaited_ code gets canceled.
    //           It is sometimes mistakenly considered an error, but it is not – [see Daniil Ovchinnikov, private conversation]
    catch (ree: RejectedExecutionException) {
      // EA-225412: FJP throws REE (which propagates through futures), e.g., when FJP reaches max threads
      // while compensating for too many managedBlockers – or when it is shutdown.

      // This branch creates a risk of infinite loop – i.e., if the current thread itself is somehow
      // responsible for FJP resource exhaustion, hence can't release anything, each consequent
      // future.get() will throw the same REE again and again. So let's limit retries:

      rejectedExecutions++
      if (rejectedExecutions > MAX_REJECTED_EXECUTIONS_BEFORE_CANCELLATION) {
        //RC: It would be clearer to rethrow ree itself – but I doubt many callers are ready for it,
        //    while all callers are ready for PCE, hence...
        throw ProcessCanceledException(ree)
      }
    }
    catch (e: InterruptedException) {
      throw ProcessCanceledException(e)
    }
    catch (e: Throwable) {
      val cause = e.cause
      if (cause is ProcessCanceledException) {
        throw cause
      }
      if (cause is CancellationException) {
        throw ProcessCanceledException(cause)
      }
      ExceptionUtil.rethrow(e)
    }
  }
}

fun Lock.awaitWithCheckCanceled() {
  awaitWithCheckCanceled(ThrowableComputable { tryLock(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS) })
}

fun awaitWithCheckCanceled(waiter: ThrowableComputable<Boolean, out Throwable>) {
  @Suppress("UsagesOfObsoleteApi")
  val indicator = ProgressManager.getInstance().getProgressIndicator()
  var success = false
  while (!success) {
    checkCancelledEvenWithPCEDisabled(indicator)
    try {
      success = waiter.compute()
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Throwable) {
      if (e !is InterruptedException) {
        LOG.error(e)
      }
      throw ProcessCanceledException(e)
    }
  }
}

/** Use when a deadlock is possible otherwise. */
@ApiStatus.Internal
fun checkCancelledEvenWithPCEDisabled(indicator: ProgressIndicator?) {
  @Suppress("UsagesOfObsoleteApi")
  val isNonCancelable = Cancellation.isInNonCancelableSection()
  if (isNonCancelable || indicator == null) {
    ProgressManager.getInstanceOrNull()?.runCheckCanceledHooks(indicator)
  }
  if (isNonCancelable) return
  Cancellation.ensureActive()
  if (indicator == null) return
  indicator.checkCanceled()     // check for cancellation as usual and run the hooks
  if (indicator.isCanceled()) { // if a just-canceled indicator or PCE is disabled
    indicator.checkCanceled()        // ... let the just-canceled indicator provide a customized PCE
    throw ProcessCanceledException() // ... otherwise PCE is disabled, so throw it manually
  }
}
