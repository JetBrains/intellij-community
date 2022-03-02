// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ObservableOperationTraceUtil")

package com.intellij.openapi.observable.operations

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.AbstractObservableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.util.ConcurrencyUtil

/**
 * Subscribes listener on operation start event that will be unsubscribed immediately before execution.
 * [parentDisposable] is used for early unsubscription when listener isn't called.
 * @see ObservableOperationTrace.beforeOperation(Int, () -> Unit, Disposable)
 */
fun ObservableOperationTrace.onceBeforeOperation(listener: () -> Unit, parentDisposable: Disposable) =
  beforeOperation(ttl = 1, listener, parentDisposable)

/**
 * Subscribes listener on operation finish event that will be unsubscribed immediately before execution.
 * @see onceBeforeOperation
 */
fun ObservableOperationTrace.onceAfterOperation(listener: () -> Unit, parentDisposable: Disposable) =
  afterOperation(ttl = 1, listener, parentDisposable)

/**
 * Executed [listener] if operation completed or after operation.
 * @param listener will be executed only once.
 * @param parentDisposable is used for early unsubscription when listener isn't called.
 */
fun ObservableOperationTrace.whenOperationCompleted(parentDisposable: Disposable, listener: () -> Unit) {
  val once = ConcurrencyUtil.once(listener)
  onceAfterOperation(once::run, parentDisposable)
  if (isOperationCompleted()) {
    once.run()
  }
}

/**
 * Returns observable property that changed before and after operation.
 * And result of [ObservableProperty.get] is equal to [ObservableOperationTrace.isOperationCompleted].
 */
fun ObservableOperationTrace.asProperty(): ObservableProperty<Boolean> {
  return object : AbstractObservableProperty<Boolean>() {
    override fun get() = isOperationCompleted()

    init {
      beforeOperation { fireChangeEvent(false) }
      afterOperation { fireChangeEvent(true) }
    }
  }
}

/**
 * Creates compound operation from several parallel operations.
 * When tasks of new operation are children [operations].
 * @see CompoundParallelOperationTrace
 */
fun compound(vararg operations: ObservableOperationTrace, debugName: String? = null): ObservableOperationTrace {
  val compound = CompoundParallelOperationTrace<ObservableOperationTrace>(debugName)
  for (operation in operations) {
    operation.beforeOperation { compound.startTask(operation) }
    operation.afterOperation { compound.finishTask(operation) }
  }
  return compound
}
