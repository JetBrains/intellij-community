// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalContracts::class)

package fleet.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.error
import kotlin.jvm.JvmInline

/**
 * Same as [Result], but non-throwable based. Can store any kind of errors
 *
 * Note: this Either non-idiomatic one
 */
@JvmInline
@Serializable(with = EitherSerializer::class)
value class Either<out T, E> internal constructor(
  private val value: Any?,
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
   * This function is shorthand for `getOrElse { null }` (see [kotlin.collections.getOrElse]) or
   * `fold(onSuccess = { it }, onFailure = { null })` (see [kotlin.collections.fold]).
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
   * This function is shorthand for `fold(onSuccess = { null }, onFailure = { it })` (see [kotlin.collections.fold]).
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

inline fun <T, E, R> Either<T, E>.flatMap(f: (T) -> Either<R, E>): Either<R, E> =
  @Suppress("UNCHECKED_CAST")
  when (this.isValue) {
    false -> this as Either<R, E>
    else -> f(valueOrNull!!)
  }

inline fun <T, E, R> Either<T, E>.map(f: (T) -> R): Either<R, E> =
  @Suppress("UNCHECKED_CAST")
  when (this.isValue) {
    false -> this as Either<R, E>
    true -> Either.value(f(valueOrNull!!))
  }

inline fun <T, E, R> Either<T, E>.mapError(f: (E) -> R): Either<T, R> =
  @Suppress("UNCHECKED_CAST")
  when (this.isError) {
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
 * Returns the encapsulated value if this instance represents [success][Either.isValue] or the
 * result of [onError] function for the encapsulated [E] if it is [error][Either.isError].
 *
 * Note, that this function rethrows any [Throwable] exception thrown by [onError] function.
 *
 * This function is a shorthand for `fold(onValue = { it }, onError = onError)` (see [fold]).
 */
inline fun <R, T : R, E> Either<T, E>.getOrElse(onError: (error: E) -> R): R {
  contract {
    callsInPlace(onError, InvocationKind.AT_MOST_ONCE)
  }
  return when (val error = errorOrNull) {
    null -> value
    else -> onError(error)
  }
}


/**
 * Returns the encapsulated value if this instance represents [value][Either.isValue] or the
 * [defaultValue] if it is [error][Either.isError].
 *
 * This function is a shorthand for `getOrElse { defaultValue }` (see [getOrElse]).
 */
inline fun <R, T : R, E> Either<T, E>.getOrDefault(defaultValue: R): R {
  if (isError) return defaultValue
  return value
}

/**
 * Returns the result of [onValue] for the encapsulated value if this instance represents [value][Either.isValue]
 * or the result of [onError] function for the encapsulated [E] error if it is [error][Either.isError].
 *
 * Note, that this function rethrows any [Throwable] exception thrown by [onValue] or by [onError] function.
 */
inline fun <R, T, E> Either<T, E>.fold(
  onValue: (value: T) -> R,
  onError: (error: E) -> R,
): R {
  contract {
    callsInPlace(onValue, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onError, InvocationKind.AT_MOST_ONCE)
  }
  return when (val error = errorOrNull) {
    null -> onValue(value)
    else -> onError(error)
  }
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

internal class EitherSerializer<T, E>(
  private val valueSerializer: KSerializer<T>,
  private val errorSerializer: KSerializer<E>,
) : KSerializer<Either<T, E>> {

  override val descriptor: SerialDescriptor
    get() = buildClassSerialDescriptor("Either", valueSerializer.descriptor, errorSerializer.descriptor) {
      element("value", valueSerializer.descriptor, isOptional = true)
      element("error", errorSerializer.descriptor, isOptional = true)
    }

  override fun deserialize(decoder: Decoder): Either<T, E> {
    return decoder.decodeStructure(descriptor) {
      when (val element = decodeElementIndex(descriptor)) {
        0 -> Either.value(decodeSerializableElement(descriptor, 0, valueSerializer))
        1 -> Either.error(decodeSerializableElement(descriptor, 1, errorSerializer))
        else -> error("Unexpected element index: $element")
      }
    }
  }

  override fun serialize(encoder: Encoder, value: Either<T, E>) {
    encoder.encodeStructure(descriptor) {
      if (value.isValue) {
        encodeSerializableElement(descriptor, 0, valueSerializer, value.value)
      }
      else {
        encodeSerializableElement(descriptor, 1, errorSerializer, value.error)
      }
    }
  }
}