// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.async

import fleet.util.channels.channels
import fleet.util.channels.use
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

fun <T, U> StateFlow<T>.view(f: (T) -> U): StateFlow<U> {
  val self = this
  return object : StateFlow<U> {
    override val replayCache: List<U>
      get() = self.replayCache.map(f)
    override val value: U
      get() = f(self.value)

    override suspend fun collect(collector: FlowCollector<U>): Nothing {
      self.collect { t ->
        collector.emit(f(t))
      }
    }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.conflateReduce(f: (T, T) -> T): Flow<T> {
  val originalFlow = this
  return flow {
    coroutineScope {
      val nothing = Any()
      var currentValue: Any? = nothing
      val receive = originalFlow.produceIn(this)
      receive.consume {
        val conflated: ReceiveChannel<T> = produce {
          val receivedCloseMessage = AtomicBoolean(false)
          while (!receive.isClosedForReceive || currentValue != nothing) {
            @Suppress("RemoveExplicitTypeArguments")
            select<Unit> {
              if (!receivedCloseMessage.load()) {
                receive.onReceiveCatching { channelResult ->
                  if (!channelResult.isClosed) {
                    val value = channelResult.getOrThrow()
                    currentValue = if (currentValue != nothing) f(currentValue as T, value) else value
                  }
                  else {
                    receivedCloseMessage.store(true)
                  }
                }
              }
              if (currentValue != nothing) {
                onSend(currentValue as T) {
                  currentValue = nothing
                }
              }
            }
          }
        }
        emitAll(conflated)
      }
    }
  }
}

fun <T> Flow<T>.chunked(): Flow<List<T>> = chunked { it }

fun <T, K> Flow<T>.chunked(chunkFn: (List<T>) -> K): Flow<K> {
  return map { item -> listOf(item) }
    .conflateReduce { items1, items2 -> items1 + items2 }
    .map { items -> chunkFn(items) }
}

suspend fun <T : Any> Flow<T?>.firstNotNull(): T {
  return checkNotNull(this.first { it != null })
}

suspend fun <T, U : Any> Flow<T>.firstNotNull(f: suspend (T) -> U?): U {
  return checkNotNull(this.map(f).first { it != null })
}

private sealed class ChunkedEvent<T> {
  data class Payload<T>(val payload: T) : ChunkedEvent<T>()

  // this is ugly, but less ugly than dealing with type erasure.
  data class Tick<T>(val useless: Unit = Unit) : ChunkedEvent<T>()
  data class Done<T>(val useless: Unit = Unit) : ChunkedEvent<T>()
}

private fun <T> Flow<ChunkedEvent<T>>.chunkWithTimerTickOrDone(): Flow<List<T>> {
  return flow {
    var buffered = mutableListOf<T>()

    collect { event ->
      when (event) {
        is ChunkedEvent.Done -> {
          emit(buffered)
          buffered = mutableListOf()
        }
        is ChunkedEvent.Tick -> {
          emit(buffered)
          buffered = mutableListOf()
        }
        is ChunkedEvent.Payload -> buffered.add(event.payload)
      }
    }
  }
}

/*
  Slices the incoming stream into groups of events that are received within the given timeframe

  events: ---A---B--C---D-E-F----G-x
  timer:  -----|------|------|--------
  result: -----A-----BC-----DEF---Gx

 */
fun <T> Flow<T>.chunkedByTimeout(timeoutMillis: Long): Flow<List<T>> {
  val timer = flow<ChunkedEvent<T>> {
    do {
      delay(timeoutMillis)
      emit(ChunkedEvent.Tick())
    }
    while (true)
  }

  val originalFlow = this.map { ChunkedEvent.Payload(it) as ChunkedEvent<T> }
    .onCompletion { reason ->
      // before flow completes we need to flush the buffer downstream, but only in case it's an "organic" completion.
      if (reason == null) {
        emit(ChunkedEvent.Done())
      }
    }

  // The `takeUntilIncluding`-twist is needed to properly complete the underlying flow because of the `merge`:
  // nobody cancels the `timer` manually.
  // Once upper-flow completes, we want to immediately emit the buffer and complete the down-flow. Doing so manually
  // would require some heavy manual lifting or introduce a data-race.
  return merge(originalFlow, timer)
    .takeUntilInclusive { event -> event is ChunkedEvent.Done }
    .chunkWithTimerTickOrDone()
    .filter { it.isNotEmpty() }
}

fun <T, R> Iterable<Flow<T>>.combineReduce(r: R, f: suspend (R, T) -> R): Flow<R> =
  fold(flowOf(r)) { f1, f2 -> f1.combine(f2, f) }

/**
 * Collects the underlying flow with timeout. It does not matter if collection is finished "organically" or with an error.
 */
fun <T> Flow<T>.withTimeout(timeoutMillis: Long): Flow<T> {
  val that = this
  return flow {
    withTimeout(timeoutMillis) {
      emitAll(that)
    }
  }
}

/**
 * Behaves very much like `takeWhile` with the main difference that it
 * does emit the "bad" element down-flow and then immediately completes.
 */
fun <T> Flow<T>.takeUntilInclusive(predicate: (T) -> Boolean): Flow<T> {
  return transformWhile { element ->
    emit(element)
    !predicate(element)
  }
}

//TODO how is it different from produceIn?
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.consumeToChannelIn(scope: CoroutineScope): ReceiveChannel<T> {
  val flow = this
  val (send, rcv) = channels<T>()
  val job = scope.launch(start = CoroutineStart.ATOMIC) { // probably undispatched is better?
    send.use {
      flow.collect {
        send.send(it)
      }
    }
  }
  send.invokeOnClose { reason ->
    job.cancel(reason?.let { CancellationException("Channel was closed/cancelled", it) })
  }

  return rcv
}

fun <T> Flow<T>.throttleLatest(timeout: Duration): Flow<T> =
  throttleLatest(timeout.toDelayMillis())

fun <T> Flow<T>.throttleLatest(delayMillis: Long): Flow<T> = this
  .conflate()
  .transform {
    emit(it)
    delay(delayMillis)
  }

private fun Duration.toDelayMillis(): Long = when (isPositive()) {
  true -> plus(999_999L.nanoseconds).inWholeMilliseconds
  false -> 0L
}