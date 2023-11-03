// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import com.intellij.collaboration.util.HashingUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.cancelOnDispose
import com.intellij.util.childScope
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.HashingStrategy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Prefer creating a service to supply a parent scope
 */
@ApiStatus.Obsolete
@Suppress("FunctionName")
fun DisposingMainScope(parentDisposable: Disposable): CoroutineScope {
  return MainScope().also {
    Disposer.register(parentDisposable) {
      it.cancel()
    }
  }
}

/**
 * Prefer creating a service to supply a parent scope
 */
@ApiStatus.Obsolete
fun Disposable.disposingMainScope(): CoroutineScope = DisposingMainScope(this)

/**
 * Prefer creating a service to supply a parent scope
 */
@ApiStatus.Obsolete
fun Disposable.disposingScope(context: CoroutineContext = SupervisorJob()): CoroutineScope = CoroutineScope(context).also {
  Disposer.register(this) {
    it.cancel()
  }
}

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

fun CoroutineScope.cancelledWith(disposable: Disposable): CoroutineScope = apply {
  val job = coroutineContext[Job]
  requireNotNull(job) { "Coroutine scope without a parent job $this" }
  job.cancelOnDispose(disposable)
}

fun CoroutineScope.launchNow(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit): Job =
  launch(context, CoroutineStart.UNDISPATCHED, block)

/**
 * Similar to [launchIn], but starts the collection with [CoroutineStart.UNDISPATCHED] and allows overriding context
 */
fun <T> Flow<T>.launchNowIn(scope: CoroutineScope, context: CoroutineContext = EmptyCoroutineContext): Job =
  scope.launch(context, CoroutineStart.UNDISPATCHED) {
    collect()
  }

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
fun <T, R> Flow<T>.mapScoped(mapper: CoroutineScope.(T) -> R): Flow<R> {
  return transformLatest { newValue ->
    coroutineScope {
      emit(mapper(newValue))
      awaitCancellation()
    }
  }
}

/**
 * Performs mapping only if the source value is not null
 */
@ApiStatus.Experimental
fun <T, R> Flow<T?>.mapNullable(mapper: (T) -> R): Flow<R?> = map { it?.let(mapper) }

/**
 * Performs mapping only if the source value is not null
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Experimental
fun <T, R> Flow<T?>.mapNullableLatest(mapper: suspend (T) -> R): Flow<R?> = mapLatest {
  if (it != null) mapper(it) else null
}

@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Experimental
fun <T, R> Flow<T?>.mapNullableScoped(mapper: CoroutineScope.(T) -> R): Flow<R?> {
  return transformLatest { newValue ->
    if (newValue == null) {
      emit(null)
    }
    else coroutineScope {
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

/**
 * Associate each *item* [T] *key* [K] in the iterable from the receiver flow (source list) with a *value* [V]
 * Keys are distinguished by a [hashingStrategy]
 *
 * When a new iterable is received:
 * * a new [CoroutineScope] and a new value is created via [valueExtractor] for new items
 * * existing values are updated via [update] if it was supplied
 * * values for missing items are removed and destroyed via [destroy]
 *
 * Order of the values in the resulting map is the same as in the source iterable
 * All [CoroutineScope]'s of values are only active while the resulting flow is being collected
 */
fun <T, K, V> Flow<Iterable<T>>.associateCachingBy(keyExtractor: (T) -> K,
                                                   hashingStrategy: HashingStrategy<K>,
                                                   valueExtractor: CoroutineScope.(T) -> V,
                                                   destroy: suspend V.() -> Unit,
                                                   update: (suspend V.(T) -> Unit)? = null)
  : Flow<Map<K, V>> = channelFlow {
  val cs = this
  var initial = true
  var prevResult = createLinkedMap<K, V>(hashingStrategy)

  collect { items ->
    var hasStructureChanges = false
    val newItemsSet = CollectionFactory.createLinkedCustomHashingStrategySet(hashingStrategy).also {
      items.mapTo(it, keyExtractor)
    }

    // remove missing
    val iter = prevResult.iterator()
    while (iter.hasNext()) {
      val (key, exisingResult) = iter.next()
      if (!newItemsSet.contains(key)) {
        iter.remove()
        hasStructureChanges = true
        exisingResult.destroy()
      }
    }

    val result = createLinkedMap<K, V>(hashingStrategy)
    // add new or update existing
    for (item in items) {
      val itemKey = keyExtractor(item)
      val existing = prevResult[itemKey]
      if (existing == null) {
        result[itemKey] = valueExtractor(cs.childScope(), item)
        hasStructureChanges = true
      }
      else {
        if (update != null) existing.update(item)
        result[itemKey] = existing
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

private fun <T, R> createLinkedMap(hashingStrategy: HashingStrategy<T>): MutableMap<T, R> =
  CollectionFactory.createLinkedCustomHashingStrategyMap(hashingStrategy)

/**
 * @see associateCachingBy
 *
 * Shorthand for cases where key is the same as item destructor simply cancels the value scope
 */
private fun <T, R> Flow<Iterable<T>>.associateCaching(hashingStrategy: HashingStrategy<T>,
                                                      mapper: CoroutineScope.(T) -> R,
                                                      update: (suspend R.(T) -> Unit)? = null): Flow<Map<T, ScopingWrapper<R>>> {
  val updater: (suspend ScopingWrapper<R>.(T) -> Unit)? = if (update != null) {
    { value.update(it) }
  }
  else null
  return associateCachingBy({ it }, hashingStrategy, { ScopingWrapper(this, mapper(it)) }, { cancel() }, updater)
}

private data class ScopingWrapper<T>(val scope: CoroutineScope, val value: T) {
  suspend fun cancel() = scope.cancelAndJoinSilently()
}

fun <ID : Any, T, R> Flow<Iterable<T>>.mapCaching(sourceIdentifier: (T) -> ID,
                                                  mapper: CoroutineScope.(T) -> R,
                                                  destroy: suspend R.() -> Unit,
                                                  update: (suspend R.(T) -> Unit)? = null): Flow<List<R>> =
  associateCachingBy(sourceIdentifier, HashingStrategy.canonical(), mapper, destroy, update).map { it.values.toList() }

/**
 * Creates a list of model objects from DTOs
 */
fun <T, R> Flow<Iterable<T>>.mapDataToModel(sourceIdentifier: (T) -> Any,
                                            mapper: CoroutineScope.(T) -> R,
                                            update: (suspend R.(T) -> Unit)): Flow<List<R>> =
  associateCaching(HashingUtil.mappingStrategy(sourceIdentifier), mapper, update).map { it.values.mapTo(mutableListOf()) { it.value } }

/**
 * Create a list of view models from models
 */
fun <T, R> Flow<Iterable<T>>.mapModelsToViewModels(mapper: CoroutineScope.(T) -> R): Flow<List<R>> =
  associateCaching(HashingStrategy.identity(), mapper).map { it.values.mapTo(mutableListOf()) { it.value } }

fun <T> Flow<Collection<T>>.mapFiltered(predicate: (T) -> Boolean): Flow<List<T>> = map { it.filter(predicate) }

/**
 * Treats 'this' flow as representing a single list of results. Meaning each emitted value is accumulated
 * with the previous ones into a single list and re-emitted.
 */
fun <T> Flow<List<T>>.collectBatches(): Flow<List<T>> {
  val result = mutableListOf<T>()
  return transform {
    result.addAll(it)
    emit(result.toList())
  }
}

/**
 * Maps values in the flow to successful results and catches and wraps any exception into a failure result.
 */
fun <T> Flow<T>.asResultFlow(): Flow<Result<T>> =
  map { Result.success(it) }.catch { emit(Result.failure(it)) }

/**
 * Maps a flow or results to a flow of a mapped result
 */
fun <T, R> Flow<Result<T>>.mapCatching(mapper: suspend (T) -> R): Flow<Result<R>> =
  map { it.mapCatching { value -> mapper(value) } }

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
