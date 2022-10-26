// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

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
fun <T, M> StateFlow<T>.mapState(
  scope: CoroutineScope,
  mapper: (value: T) -> M
): StateFlow<M> = map { mapper(it) }.stateIn(scope, SharingStarted.Eagerly, mapper(value))

@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Experimental
fun <T, R> StateFlow<T>.mapStateScoped(scope: CoroutineScope,
                                       sharingStart: SharingStarted = SharingStarted.Eagerly,
                                       mapper: (CoroutineScope, T) -> R): StateFlow<R> {
  var nestedScope: CoroutineScope = scope.childScope()
  val originalState = this
  return drop(1).transformLatest { newValue ->
    nestedScope.cancel()
    nestedScope = scope.childScope()
    val mapped = mapper(nestedScope, newValue)
    emit(mapped)
  }.stateIn(scope, sharingStart, mapper(nestedScope, originalState.value))
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