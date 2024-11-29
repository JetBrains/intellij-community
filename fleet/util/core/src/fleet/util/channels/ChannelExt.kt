// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.channels

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.SelectBuilder
import kotlinx.coroutines.selects.select

inline fun <T, R> SendChannel<T>.use(block: (SendChannel<T>) -> R): R {
  var cause: Throwable? = null
  try {
    return block(this)
  }
  catch (ex: Throwable) {
    cause = ex
    throw ex
  }
  finally {
    close(cause)
  }
}

fun <T> CoroutineScope.batching(source: ReceiveChannel<T>,
                                sink: SendChannel<List<T>>) {
  launch {
    var acc = ArrayList<T>()
    var loop = true
    while (loop) {
      loop = select {
        source.onReceiveCatching { t ->
          if (t.isClosed) {
            sink.send(acc)
            sink.close()
            false
          }
          else {
            acc.add(t.getOrThrow())
            true
          }
        }
        if (acc.isNotEmpty()) {
          sink.onSend(acc) {
            acc = ArrayList()
            true
          }
        }
      }
    }
  }
}

fun <T> CoroutineScope.debounce(source: ReceiveChannel<T>,
                                sink: SendChannel<T>,
                                millis: Long) {
  launch {
    try {
      source.consumeEach { t ->
        sink.send(t)
        delay(millis)
      }
    }
    finally {
      sink.close()
    }
  }
}

suspend fun <T> consumeAll(vararg channels: ReceiveChannel<*>, body: suspend CoroutineScope.() -> T): T = consumeAll(channels, 0, body)

suspend fun <T> useAll(vararg channels: SendChannel<*>?, body: suspend CoroutineScope.() -> T): T = useAll(channels, 0, body)

suspend inline fun consumeAllAndSelect(vararg channels: ReceiveChannel<*>, crossinline builder: SelectBuilder<Boolean>.() -> Unit) {
  consumeAll(*channels) {
    @Suppress("ControlFlowWithEmptyBody")
    while (!select(builder));
  }
}

suspend fun <T> consumeEach(vararg channels: ReceiveChannel<T>, body: suspend (T) -> Unit) {
  consumeAll(*channels) {
    while (true) {
      val open = channels.filter { !it.isClosedForReceive }
      if (open.isNotEmpty()) {
        @Suppress("RemoveExplicitTypeArguments")
        select<Unit> {
          for (channel in open) {
            channel.onReceiveCatching { res ->
              if (!res.isClosed) {
                body(res.getOrThrow())
              }
            }
          }
        }
      }
      else {
        break
      }
    }
  }
}

private suspend fun <T> useAll(channels: Array<out SendChannel<*>?>, index: Int, body: suspend CoroutineScope.() -> T): T {
  return if (index == channels.size) {
    coroutineScope { body() }
  }
  else {
    val channel = channels[index]
    if (channel != null) {
      channel.use {
        useAll(channels, index + 1, body = body)
      }
    }
    else {
      useAll(channels, index + 1, body = body)
    }
  }
}

private suspend fun <T> consumeAll(channels: Array<out ReceiveChannel<*>>, index: Int, body: suspend CoroutineScope.() -> T): T {
  return if (index == channels.size) {
    coroutineScope { body() }
  }
  else {
    channels[index].consume {
      consumeAll(channels, index + 1, body = body)
    }
  }
}

fun <T> Channel<T>.split(): Pair<SendChannel<T>, ReceiveChannel<T>> = this to this

fun <T> channels(
  capacity: Int = Channel.RENDEZVOUS,
  onBufferOverlow: BufferOverflow = BufferOverflow.SUSPEND,
): Pair<SendChannel<T>, ReceiveChannel<T>> = Channel<T>(capacity, onBufferOverflow = onBufferOverlow).split()

val <T> ChannelResult<T>.isFull get() = isFailure && !isClosed
