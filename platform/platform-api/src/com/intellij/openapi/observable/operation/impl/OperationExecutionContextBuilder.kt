// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.operation.OperationExecutionContext
import com.intellij.openapi.observable.operation.OperationExecutionContext.ContextKey
import com.intellij.openapi.observable.util.whenDisposed
import org.jetbrains.annotations.ApiStatus
import java.util.Optional

@ApiStatus.Internal
class OperationExecutionContextBuilder : OperationExecutionContext.Builder {

  private val data = LinkedHashMap<ContextKey<*>, Optional<Any>>()

  override fun <T> findData(key: ContextKey<T>): T? {
    @Suppress("UNCHECKED_CAST")
    return data[key]?.orElse(null) as T?
  }

  override fun <T> getData(key: ContextKey<T>): T {
    require(key in data) { "Cannot find $key in $this" }
    @Suppress("UNCHECKED_CAST")
    return data[key]!!.orElse(null) as T
  }

  override fun <T> putData(key: ContextKey<T>, data: T, parentDisposable: Disposable?) {
    this.data[key] = Optional.ofNullable(data)
    parentDisposable?.whenDisposed {
      this.data.remove(key)
    }
  }

  override fun toString(): String {
    return data.mapValues { it.value.orElse("null").toString() }
      .mapValues { if (" " in it.value) "[" + it.value + "]" else it.value }
      .toString()
  }
}