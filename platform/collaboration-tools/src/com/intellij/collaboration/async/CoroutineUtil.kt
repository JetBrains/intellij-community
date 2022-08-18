// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

@ApiStatus.Experimental
@Suppress("FunctionName")
fun DisposingMainScope(parentDisposable: Disposable): CoroutineScope =
  MainScope().also {
    Disposer.register(parentDisposable) {
      it.cancel()
    }
  }

@ApiStatus.Experimental
interface ScopedDisposable : Disposable {
  val scope: CoroutineScope
}

@ApiStatus.Experimental
class CancellingScopedDisposable(context: CoroutineContext = SupervisorJob()) : ScopedDisposable {

  override val scope: CoroutineScope = CoroutineScope(context)

  override fun dispose() {
    scope.cancel()
  }
}

@ApiStatus.Experimental
fun <T1, T2, R> combineState(scope: CoroutineScope,
                             state1: StateFlow<T1>,
                             state2: StateFlow<T2>,
                             transform: (T1, T2) -> R): StateFlow<R> =
  combine(state1, state2, transform)
    .stateIn(scope, SharingStarted.Eagerly, transform(state1.value, state2.value))