// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.DbContext
import fleet.kernel.rebase.RebaseLoopStateDebugKernelMetaKey
import fleet.kernel.rebase.clientClock
import fleet.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

private suspend fun captureVectorClock(): CompressedVectorClock =
  requireNotNull(db().clientClock).compressed()

class TimedOutWaitingForClockException(msg: String, cause: Throwable? = null)
  : RuntimeException(msg, cause),
    CopyableThrowable<TimedOutWaitingForClockException> {
  override fun createCopy(): TimedOutWaitingForClockException {
    return TimedOutWaitingForClockException(message!!, this)
  }
}

suspend fun CompressedVectorClock.await(timeout_millis: Long = 30_000) {
  val clock = this
  var lastObservedClock: VectorClock? = null
  val dbSource = coroutineContext.dbSource
  withTimeoutOrNull(timeout_millis) {
    val db = dbSource.flow.firstOrNull { db ->
      db.clientClock?.let { clientClock ->
        val vectorClock = clientClock.vectorClock
        lastObservedClock = vectorClock
        clock.precedesOrEqual(vectorClock)
      } ?: true
    } ?: throw CancellationException("Kernel is terminated")
    DbContext.threadBound.set(db)
    yield()
  } ?: run {
    val rebaseLog = coroutineContext.transactor.meta[RebaseLoopStateDebugKernelMetaKey]?.load()
    throw TimedOutWaitingForClockException(
      "$dbSource Timed out waiting for clock $clock, last observed clock: $lastObservedClock".letIf(rebaseLog != null) { message ->
        message + "\n" + rebaseLog!!.rebaseLog.debugString()
      })
  }
}


/**
 * Captures a value in [Causal]
 * Requires []
 * Meant to be used to pass data associated with DB in RPC channels
 * The receiver of [Causal] may [await] on it to ensure all the changes seen by the capturer is consumed by receiver's local [Transactor]
 * */
suspend fun <T> causal(value: T): Causal<T> {
  return Causal(value, captureVectorClock(), LocalDbTimestamp(transactor(), db().timestamp))
}

suspend fun LocalDbTimestamp.await() {
  require(transactor() == kernelId) { "waiting on different kernel ${transactor()} than mine $kernelId" }
  waitForDbSourceToCatchUpWithTimestamp(timestamp)
}

/**
 * Suspends until all the changes as of the moment of [Causal]'s capturing are received by the [Transactor] in [saga]
 * Requires []
 * */
suspend fun <T> Causal<T>.await(): T {
  when {
    localDbTimestamp?.kernelId == transactor() -> localDbTimestamp!!.await()
    else -> clock.await()
  }
  return value
}
