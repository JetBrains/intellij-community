// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

/**
 * Represents the state of computing some value of type [T]
 *
 * null value means that the computation hasn't completed yet
 */
@JvmInline
value class ComputedResult<out T> private constructor(
  val result: Result<T>?
) {
  val isInProgress: Boolean get() = result == null

  companion object {
    fun <T> loading(): ComputedResult<T> = ComputedResult(null)
    fun <T> success(value: T): ComputedResult<T> = ComputedResult(Result.success(value))
    fun <T> failure(error: Throwable): ComputedResult<T> = ComputedResult(Result.failure(error))
  }
}