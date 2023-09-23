// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.HashingStrategy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@ApiStatus.Experimental
@Suppress("FunctionName")
fun DisposingMainScope(parentDisposable: Disposable): CoroutineScope {
  return MainScope().also {
    Disposer.register(parentDisposable) {
      it.cancel()
    }
  }
}

@ApiStatus.Experimental
fun Disposable.disposingMainScope(): CoroutineScope = DisposingMainScope(this)

@ApiStatus.Experimental
@Suppress("FunctionName")
fun DisposingScope(parentDisposable: Disposable, context: CoroutineContext = SupervisorJob()): CoroutineScope {
  return CoroutineScope(context).also {
    Disposer.register(parentDisposable) {
      it.cancel()
    }
  }
}

@ApiStatus.Experimental
fun Disposable.disposingScope(context: CoroutineContext = SupervisorJob()): CoroutineScope = DisposingScope(this, context)

@OptIn(InternalCoroutinesApi::class)
@ApiStatus.Experimental
fun CoroutineScope.nestedDisposable(): Disposable {
  val job = coroutineContext[Job]
  require(job != null) {
    "Found no Job in context: $coroutineContext"
  }
  return Disposer.newDisposable().also {
    job.invokeOnCompletion(onCancelling = true, handler = { _ ->
      Disposer.dispose(it)
    })
  }
}

fun CoroutineScope.launchNow(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit): Job =
  launch(context, CoroutineStart.UNDISPATCHED, block)

@ApiStatus.Experimental
fun <T1, T2, R> combineState(scope: CoroutineScope,
                             state1: StateFlow<T1>,
                             state2: StateFlow<T2>,
                             transform: (T1, T2) -> R): StateFlow<R> =
  combine(state1, state2, transform)
    .stateIn(scope, SharingStarted.Eagerly, transform(state1.value, state2.value))

@ApiStatus.Experimental
fun <T1, T2, T3, R> combineState(scope: CoroutineScope,
                                 state1: StateFlow<T1>,
                                 state2: StateFlow<T2>,
                                 state3: StateFlow<T3>,
                                 transform: (T1, T2, T3) -> R): StateFlow<R> =
  combine(state1, state2, state3, transform)
    .stateIn(scope, SharingStarted.Eagerly, transform(state1.value, state2.value, state3.value))

@ApiStatus.Experimental
suspend fun <T1, T2> combineAndCollect(
  flow1: Flow<T1>,
  flow2: Flow<T2>,
  action: (T1, T2) -> Unit
) {
  return combine(flow1, flow2) { value1, value2 ->
    value1 to value2
  }.collect { (value1, value2) ->
    action(value1, value2)
  }
}

@ApiStatus.Experimental
suspend fun <T1, T2, T3> combineAndCollect(
  flow1: Flow<T1>,
  flow2: Flow<T2>,
  flow3: Flow<T3>,
  action: (T1, T2, T3) -> Unit
) {
  return combine(flow1, flow2, flow3) { value1, value2, value3 ->
    Triple(value1, value2, value3)
  }.collect { (value1, value2, value3) ->
    action(value1, value2, value3)
  }
}

fun Flow<Boolean>.inverted() = map { !it }

@ApiStatus.Experimental
fun <T, M> StateFlow<T>.mapState(
  scope: CoroutineScope,
  mapper: (value: T) -> M
): StateFlow<M> = map { mapper(it) }.stateIn(scope, SharingStarted.Eagerly, mapper(value))

@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Experimental
fun <T, R> Flow<T>.mapScoped(mapper: suspend CoroutineScope.(T) -> R): Flow<R> {
  return transformLatest { newValue ->
    coroutineScope {
      emit(mapper(newValue))
      awaitCancellation()
    }
  }
}

@ApiStatus.Experimental
suspend fun <T> StateFlow<T>.collectScoped(collector: (CoroutineScope, T) -> Unit) {
  collectLatest { state ->
    coroutineScope {
      val nestedScope = this
      collector(nestedScope, state)
      awaitCancellation()
    }
  }
}

@ApiStatus.Experimental
suspend fun <T> Flow<T>.collectWithPrevious(initial: T, collector: suspend (prev: T, current: T) -> Unit) {
  var prev = initial
  collect {
    collector(prev, it)
    prev = it
  }
}

@ApiStatus.Experimental
fun <T> Flow<T>.withInitial(initial: T): Flow<T> = flow {
  emit(initial)
  emitAll(this@withInitial)
}

/**
 * Lazy shared flow that logs all exceptions as errors and never throws (beside cancellation)
 */
fun <T> Flow<T>.modelFlow(cs: CoroutineScope, log: Logger): SharedFlow<T> =
  catch { log.error(it) }.shareIn(cs, SharingStarted.Lazily, 1)

fun <ID : Any, T, R> Flow<Iterable<T>>.associateBy(sourceIdentifier: (T) -> ID,
                                                   mapper: CoroutineScope.(T) -> R,
                                                   destroy: suspend R.() -> Unit,
                                                   update: (suspend R.(T) -> Unit)? = null,
                                                   customHashingStrategy: HashingStrategy<ID>? = null)
  : Flow<Map<ID, R>> = channelFlow {
  val cs = this
  var initial = true
  var prevResult = createLinkedMap<ID, R>(customHashingStrategy)

  collect { items ->
    var hasStructureChanges = false
    val newItemsIdSet = if (customHashingStrategy == null) {
      items.mapTo(LinkedHashSet(), sourceIdentifier)
    }
    else {
      CollectionFactory.createLinkedCustomHashingStrategySet(customHashingStrategy).let {
        items.mapTo(it, sourceIdentifier)
      }
    }

    // remove missing
    val iter = prevResult.iterator()
    while (iter.hasNext()) {
      val (key, exisingResult) = iter.next()
      if (!newItemsIdSet.contains(key)) {
        iter.remove()
        hasStructureChanges = true
        exisingResult.destroy()
      }
    }

    val result = createLinkedMap<ID, R>(customHashingStrategy)
    // add new or update existing
    for (item in items) {
      val id = sourceIdentifier(item)

      val existing = prevResult[id]
      if (existing != null && update != null) {
        existing.update(item)
        result[id] = existing
      }
      else {
        result[id] = mapper(cs, item)
        hasStructureChanges = true
      }
    }

    prevResult = result
    if (hasStructureChanges || initial) {
      initial = false
      send(result)
    }
  }
  awaitClose()
}

private fun <ID : Any, R> createLinkedMap(customHashingStrategy: HashingStrategy<ID>?): MutableMap<ID, R> =
  if (customHashingStrategy == null) {
    LinkedHashMap()
  }
  else {
    CollectionFactory.createLinkedCustomHashingStrategyMap(customHashingStrategy)
  }

fun <ID : Any, T, R> Flow<Iterable<T>>.mapCaching(sourceIdentifier: (T) -> ID,
                                                  mapper: CoroutineScope.(T) -> R,
                                                  destroy: suspend R.() -> Unit,
                                                  update: (suspend R.(T) -> Unit)? = null): Flow<List<R>> =
  associateBy(sourceIdentifier, mapper, destroy, update).map { it.values.toList() }

fun <T> Flow<Collection<T>>.mapFiltered(predicate: (T) -> Boolean): Flow<List<T>> = map { it.filter(predicate) }

/**
 * Treats 'this' flow as representing a single list of results. Meaning each emitted value is accumulated
 * with the previous ones into a single list and re-emitted.
 *
 * This emits the same mutable list every time, new results in upstream flow will thus modify the previously
 * emitted list. Take note of this when using the emitted list directly.
 */
fun <T> Flow<List<T>>.collectBatches(): Flow<List<T>> {
  val result = mutableListOf<T>()
  return transform {
    result.addAll(it)
    emit(result)
  }
}

/**
 * Maps values in the flow to successful results and catches and wraps any exception into a failure result.
 */
fun <T> Flow<T>.asResultFlow(): Flow<Result<T>> =
  map { Result.success(it) }.catch { emit(Result.failure(it)) }

/**
 * Maps a flow or results to a flow of values from successful results. Failure results are re-thrown as exceptions.
 */
fun <T> Flow<Result<T>>.throwFailure(): Flow<T> =
  map { it.getOrThrow() }

/**
 * Cancel the scope, await its completion but ignore the completion exception if any to void cancelling the caller
 */
suspend fun CoroutineScope.cancelAndJoinSilently() {
  val cs = this
  cs.coroutineContext[Job]?.cancelAndJoinSilently() ?: error("Missing Job in $this")
}

/**
 * Cancel the job, await its completion but ignore the completion exception if any to void cancelling the caller
 */
suspend fun Job.cancelAndJoinSilently() {
  val job = this
  try {
    job.cancelAndJoin()
  }
  catch (ignored: Exception) {
  }
}
