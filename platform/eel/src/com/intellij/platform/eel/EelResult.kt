// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

sealed interface EelResult<out P, out E> {
  interface Ok<out P, out E> : EelResult<P, E> {
    val value: P
  }

  interface Error<out P, out E> : EelResult<P, E> {
    val error: E
  }
}

@JvmOverloads
inline fun <T, E> EelResult<T, E>.getOrThrow(action: (E) -> Nothing = { throw RuntimeException(it.toString()) }): T = when (this) {
  is EelResult.Ok -> this.value
  is EelResult.Error -> action(this.error)
}

fun <T, E> EelResult<T, E>.getOrNull(): T? = when (this) {
  is EelResult.Ok -> this.value
  is EelResult.Error -> null
}
