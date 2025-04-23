// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus

/**
 * [EelResult] is not completely deprecated yet, because the API that should replace it being developed.
 * Nevertheless, prepare for the upcoming deprecation.
 * Refrain from making new extension functions for [EelResult] and for using it in state of objects, putting EelResults into collections, etc.
 */
@ApiStatus.Obsolete
sealed interface EelResult<out P, out E /*: EelError*//* TODO Uncomment and fix usages. */> {
  interface Ok<out P> : EelResult<P, Nothing> {
    val value: P
  }

  interface Error<out E> : EelResult<Nothing, E> {
    val error: E
  }
}

@ApiStatus.NonExtendable
interface EelError {
  object Unknown : EelError
}

/***
 * ```kotlin
 *  val data = someFun().getOr { return it }
 * ```
 */
inline fun <T, E> EelResult<T, E>.getOr(action: (EelResult.Error<E>) -> Nothing): T = when (this) {
  is EelResult.Ok -> this.value
  is EelResult.Error -> action(this)
}

@JvmOverloads
inline fun <T, E> EelResult<T, E>.getOrThrow(exception: (E) -> Throwable = { if (it is Throwable) it else RuntimeException(it.toString()) }): T = getOr { throw exception(it.error) }

suspend inline fun <T, E, R, O> O.getOrThrow(exception: (E) -> Throwable = { if (it is Throwable) it else RuntimeException(it.toString()) }): T where R : EelResult<T, E>, O : OwnedBuilder<R> =
  eelIt().getOrThrow(exception)

fun <T, E> EelResult<T, E>.getOrNull(): T? = when (this) {
  is EelResult.Ok -> this.value
  is EelResult.Error -> null
}