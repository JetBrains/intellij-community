// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui

import com.intellij.collaboration.messages.CollaborationToolsBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.net.ConnectException
import java.nio.channels.UnresolvedAddressException

object ExceptionUtil {

  fun getPresentableMessage(exception: Throwable): @Nls String {
    if (exception.localizedMessage != null) return exception.localizedMessage

    if (exception is ConnectException) {
      if (exception.cause is UnresolvedAddressException) {
        return CollaborationToolsBundle.message("error.address.unresolved")
      }
      return CollaborationToolsBundle.message("error.connection.error")
    }
    return CollaborationToolsBundle.message("error.unknown")
  }
}

/**
 * Represents a union type. Either the left value or the right value is present.
 * The right value is by convention the most expected value while the left value is
 * usually the exceptional value.
 *
 * You can use this class to model some outcome that may fail. Similar to a Result,
 * but with a specific error type. For instance `Either<Exception, Int>` may model
 * the result of a division. The result will then either be some `DivideByZeroException`
 * for instance, or a simple number.
 */
@ApiStatus.Internal
sealed interface Either<out A, out B> {
  fun asLeftOrNull(): A? = if (this is Left) value else null
  fun asRightOrNull(): B? = if (this is Right) value else null

  fun isLeft(): Boolean = this is Left
  fun isRight(): Boolean = this is Right

  fun <A2, B2> bimap(ifLeft: (A) -> A2, ifRight: (B) -> B2) = when (this) {
    is Left -> Left(ifLeft(value))
    is Right -> Right(ifRight(value))
  }

  fun <C> fold(ifLeft: (A) -> C, ifRight: (B) -> C) : C = when (this) {
    is Left -> ifLeft(value)
    is Right -> ifRight(value)
  }

  @JvmInline
  private value class Left<A>(val value: A) : Either<A, Nothing>
  @JvmInline
  private value class Right<B>(val value: B) : Either<Nothing, B>

  companion object {
    fun <A> left(value: A): Either<A, Nothing> = Left(value)
    fun <B> right(value: B): Either<Nothing, B> = Right(value)
  }
}
