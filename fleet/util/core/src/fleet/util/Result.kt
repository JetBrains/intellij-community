// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

fun <T> Result<T?>.ensure(message: String): Result<T> = andThen {
  when (it) {
    null -> Result.failure(IllegalArgumentException(message))
    else -> Result.success(it)
  }
}

inline fun <T, R> Result<T>.andThen(next: (T) -> Result<R>): Result<R> = fold(
  onSuccess = { next(it) },
  onFailure = { Result.failure(it) }
)

inline fun<T> Result<T>.wrapError(next: (Throwable) -> Throwable): Result<T> = fold(
  onSuccess = { Result.success(it) },
  onFailure = { Result.failure(next(it)) }
)

fun <T, R> Result.Companion.flatMap(source: Iterable<T>, transform: (T) -> Result<R>): Result<List<R>> {
  val successes = mutableListOf<R>()
  source.forEach { transform(it).fold(
    onSuccess = successes::add,
    onFailure = { err -> return failure(err) }
  ) }
  return success(successes)
}