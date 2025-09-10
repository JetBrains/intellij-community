// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util


inline fun <T, R> Result<T>.andThen(next: (T) -> Result<R>): Result<R> = fold(
  onSuccess = { next(it) },
  onFailure = { Result.failure(it) }
)

inline fun<T> Result<T>.wrapError(next: (Throwable) -> Throwable): Result<T> = fold(
  onSuccess = { Result.success(it) },
  onFailure = { Result.failure(next(it)) }
)