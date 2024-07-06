// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalContracts::class)

package fleet.util

import fleet.util.serialization.DataSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Same as [Result], but non-throwable based. Can store any kind of errors
 *
 * Note: this Either non-idiomatic one
 */
@JvmInline
@Serializable(with = EitherSerializer::class)
value class Either<out T, E> internal constructor(
  private val value: Any?
) {
  /**
   * Returns `true` if this instance represents a value outcome.
   * In this case [isError] returns `false`.
   */
  val isValue: Boolean get() = value !is Error<*>

  /**
   * Returns `true` if this instance represents a error outcome.
   * In this case [isValue] returns `false`.
   */
  val isError: Boolean get() = value is Error<*>

  /**
   * Returns the encapsulated value if this instance represents [success][Either.isValue] or `null`
   * if it is [error][Either.isError].
   *
   * This function is a shorthand for `getOrElse { null }` (see [getOrElse]) or
   * `fold(onSuccess = { it }, onFailure = { null })` (see [fold]).
   */
  @Suppress("UNCHECKED_CAST")
  val valueOrNull: T?
    get() = when {
      isError -> null
      else -> value as T
    }

  /**
   * Returns the encapsulated error if this instance represents [failure][isError] or `null`
   * if it is [success][isValue].
   *
   * This function is a shorthand for `fold(onSuccess = { null }, onFailure = { it })` (see [fold]).
   */
  @Suppress("UNCHECKED_CAST")
  val errorOrNull: E?
    get() = when (value) {
      is Error<*> -> value.error as E
      else -> null
    }

  /**
   * Returns a string `Success(v)` if this instance represents [success][Result.isSuccess]
   * where `v` is a string representation of the value or a string `Failure(x)` if
   * it is [failure][isError] where `x` is a string representation of the exception.
   */
  override fun toString(): String =
    when (value) {
      is Error<*> -> "Error(${value.error}"
      else -> "Value($value)"
    }

  /**
   * Companion object for [Either] class that contains its constructor functions
   * [value] and [error].
   */
  companion object {
    /**
     * Returns an instance that encapsulates the given [value] as successful value.
     */
    fun <T, E> value(value: T): Either<T, E> = Either(value)

    /**
     * Returns an instance that encapsulates the given [error] as error.
     */
    fun <T, E> error(error: E): Either<T, E> = Either(Error(error))
  }

  @JvmInline
  private value class Error<E>(val error: E)
}

val <T, E> Either<T, E>.value: T
  get() = requireNotNull(valueOrNull) { "Not a Value" }

val <T, E> Either<T, E>.error: E
  get() = requireNotNull(errorOrNull) { "Not a Error" }

fun <T, E, R> Either<T, E>.flatMap(f: (T) -> Either<R, E>): Either<R, E> = when (this.isValue) {
  false -> this as Either<R, E>
  else -> f(valueOrNull!!)
}

fun <T, E, R> Either<T, E>.map(f: (T) -> R): Either<R, E> = when (this.isValue) {
  false -> this as Either<R, E>
  true -> Either.value(f(valueOrNull!!))
}

fun <T, E, R> Either<T, E>.mapError(f: (E) -> R): Either<T, R> = when (this.isError) {
  false -> this as Either<T, R>
  true -> Either.error(f(error))
}

/**
 * Performs the given [action] on the encapsulated [E] if this instance represents [error][Either.isError].
 * Returns the original `Either` unchanged.
 */
inline fun <T, E> Either<T, E>.onError(action: (error: E) -> Unit): Either<T, E> {
  contract {
    callsInPlace(action, InvocationKind.AT_MOST_ONCE)
  }
  errorOrNull?.let { action(it) }
  return this
}

/**
 * Performs the given [action] on the encapsulated value if this instance represents [value][Either.isValue].
 * Returns the original `Either` unchanged.
 */
inline fun <T, E> Either<T, E>.onValue(action: (value: T) -> Unit): Either<T, E> {
  contract {
    callsInPlace(action, InvocationKind.AT_MOST_ONCE)
  }
  valueOrNull?.let { action(it) }
  return this
}


private class EitherSerializer<T, E>(
  valueSerializer: KSerializer<T>,
  errorSerializer: KSerializer<E>,
) : DataSerializer<Either<T, E>, SerializableEither<T, E>>(SerializableEither.serializer(valueSerializer, errorSerializer)) {
  override fun fromData(data: SerializableEither<T, E>): Either<T, E> {
    return when (data) {
      is SerializableEither.Error -> Either.error(data.error)
      is SerializableEither.Value -> Either.value(data.value)
    }
  }

  override fun toData(value: Either<T, E>): SerializableEither<T, E> {
    return if (value.isValue) {
      SerializableEither.Value(value.valueOrNull!!)
    }
    else {
      SerializableEither.Error(value.errorOrNull!!)
    }
  }
}

@Serializable
private sealed interface SerializableEither<T, E> {
  @JvmInline
  @Serializable
  @SerialName("value")
  value class Value<T, E>(val value: T) : SerializableEither<T, E>

  @JvmInline
  @Serializable
  @SerialName("error")
  value class Error<T, E>(val error: E) : SerializableEither<T, E>
}