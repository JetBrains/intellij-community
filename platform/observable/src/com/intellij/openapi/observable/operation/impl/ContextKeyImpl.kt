// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation.impl

import com.intellij.openapi.observable.operation.OperationExecutionContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ContextKeyImpl<T>(
  private val name: String
) : OperationExecutionContext.ContextKey<T> {

  override fun toString(): String {
    return name
  }
}