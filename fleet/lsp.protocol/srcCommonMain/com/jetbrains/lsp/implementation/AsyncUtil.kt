package com.jetbrains.lsp.implementation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

internal suspend fun <T : Job, R> T.use(body: suspend CoroutineScope.(T) -> R): R {
    return try {
        coroutineScope { body(this@use) }
    } finally {
        cancelAndJoin()
    }
}

internal fun <T> Channel<T>.split(): Pair<SendChannel<T>, ReceiveChannel<T>> = this to this

internal fun <T> channels(
    capacity: Int = Channel.RENDEZVOUS,
    onBufferOverlow: BufferOverflow = BufferOverflow.SUSPEND,
): Pair<SendChannel<T>, ReceiveChannel<T>> = Channel<T>(capacity, onBufferOverflow = onBufferOverlow).split()

internal inline fun <T, R> SendChannel<T>.use(block: (SendChannel<T>) -> R): R {
    var cause: Throwable? = null
    try {
        return block(this)
    } catch (ex: Throwable) {
        cause = ex
        throw ex
    } finally {
        close(cause)
    }
}

suspend fun <T> withSupervisor(body: suspend CoroutineScope.(scope: CoroutineScope) -> T): T {
    val context = currentCoroutineContext()
    val supervisorJob = SupervisorJob(context.job)
    return try {
      coroutineScope {
        body(CoroutineScope(context + supervisorJob))
      }
    }
    finally {
      supervisorJob.cancelAndJoin()
    }
  }
  