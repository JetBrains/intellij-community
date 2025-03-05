// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

sealed interface EelResult<out P, out E> {
  interface Ok<out P> : EelResult<P, Nothing> {
    val value: P
  }

  interface Error<out E> : EelResult<Nothing, E> {
    val error: E
  }
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

fun <T, E> EelResult<T, E>.getOrNull(): T? = when (this) {
  is EelResult.Ok -> this.value
  is EelResult.Error -> null
}