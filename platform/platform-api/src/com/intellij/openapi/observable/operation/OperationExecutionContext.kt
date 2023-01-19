// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.operation.impl.ContextKeyImpl
import com.intellij.openapi.observable.operation.impl.OperationExecutionContextBuilder
import org.jetbrains.annotations.ApiStatus

/**
 * Data context for operation execution markup.
 * It allows to forward data through execution events.
 */
@ApiStatus.NonExtendable
interface OperationExecutionContext {

  fun <T> findData(key: ContextKey<T>): T?

  fun <T> getData(key: ContextKey<T>): T

  @ApiStatus.NonExtendable
  interface Builder : OperationExecutionContext {

    fun <T> putData(key: ContextKey<T>, data: T) = putData(key, data, null)
    fun <T> putData(key: ContextKey<T>, data: T, parentDisposable: Disposable?)
  }

  interface ContextKey<T>

  companion object {

    fun <T> createKey(debugName: String = "UNKNOWN"): ContextKey<T> {
      return ContextKeyImpl(debugName)
    }

    fun create(configure: Builder.() -> Unit): OperationExecutionContext {
      return OperationExecutionContextBuilder().apply(configure)
    }
  }
}