// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete

import fleet.util.hash
import kotlin.jvm.JvmInline

interface Match<out T> {

  companion object {
    fun <T> validatable(
      value: T,
      base: Match<*>? = null,
      validate: () -> ValidationResultEnum
    ): Match<T> =
      @Suppress("UNCHECKED_CAST")
      when (value) {
        is Int -> ValidatableIntMatch(value, base, validate) as Match<T>
        else -> ValidatableMatch(value, base, validate)
      }

    /**
     * Returns an always-valid [Match]. Should only be used to temporarily box values into matches.
     */
    fun <T> of(value: T): Match<T> = ValueMatch(value)

    /**
     * Reduces multiple matches into a single match using the provided operation.
     */
    fun <T> combineAll(vararg matches: Match<T>, operation: (acc: T, T) -> T): Match<T> =
      MergedMany(matches.map { it.value }.reduce(operation), matches.toList())

    fun <T> nonValidatable(value: T): Match<T> = NonValidatableMatch(value)

    fun <T> failure(x: Throwable): Match<T> = Failure(x)
  }

  val value: T

  fun validate(): ValidationResultEnum

  fun observableSubmatches(): Sequence<Match<*>>

  fun <U> find(boundQuery: BoundQuery<U>): Match<U>? = null
}

operator fun <U> Match<*>.get(boundQuery: BoundQuery<U>): U =
  requireNotNull(find(boundQuery)) { "query $boundQuery is unbound in $this" }.value

fun <T> Match<T>.bind(key: Any): Match<T> = Binding(key, this)

/**
 * attaches a new value to a match
 * */
fun <T> Match<*>.withValue(value: T): Match<T> = BasedMatch(value, this)

/**
 * Combines the left and right matches using the provided operation.
 */
fun <T, U, R> Match<T>.combine(right: Match<U>, operation: (left: T, right: U) -> R): Match<R> =
  Merged(operation(value, right.value), this, right)

/**
 * Combines the left and right matches using the provided operation.
 */
fun <T> Match<*>.combine(right: Match<*>, value: T): Match<T> =
  Merged(value, this, right)

@JvmInline
value class ValidationResultEnum private constructor(val result: Int) {
  companion object {
    val Valid = ValidationResultEnum(1)
    val Invalid = ValidationResultEnum(0)
    val Inconclusive = ValidationResultEnum(2)
  }

  fun and(rhs: () -> ValidationResultEnum): ValidationResultEnum =
    when (this) {
      Valid -> rhs()
      Invalid -> Invalid
      Inconclusive -> Inconclusive
      else -> error("not reachable")
    }
}

internal fun Boolean.asValidationResult(): ValidationResultEnum =
  when (this) {
    true -> ValidationResultEnum.Valid
    false -> ValidationResultEnum.Invalid
  }

private class Failure<T>(val x: Throwable) : Match<T> {
  override val value: T
    get() = throw RuntimeException("Query has failed", x)

  override fun validate(): ValidationResultEnum =
    ValidationResultEnum.Valid

  override fun observableSubmatches(): Sequence<Match<*>> =
    throw RuntimeException("Query has failed", x)
}

private data class Merged<T>(
  override val value: T,
  val left: Match<*>,
  val right: Match<*>
) : Match<T> {

  private val cachedHash = hash(value, left, right)

  override fun hashCode(): Int =
    cachedHash

  override fun equals(other: Any?): Boolean =
    other is Merged<*> && other.value == value && other.left == left && other.right == right

  override fun <U> find(boundQuery: BoundQuery<U>): Match<U>? =
    left.find(boundQuery) ?: right.find(boundQuery)

  override fun validate(): ValidationResultEnum =
    left.validate().and(right::validate)

  override fun observableSubmatches(): Sequence<Match<*>> =
    left.observableSubmatches() + right.observableSubmatches()
}

private data class MergedMany<T>(
  override val value: T,
  val ms: List<Match<*>>,
) : Match<T> {

  private val cachedHash = hash(value, ms)

  override fun hashCode(): Int =
    cachedHash

  override fun equals(other: Any?): Boolean =
    other is MergedMany<*> && other.value == value && other.ms == ms

  override fun <U> find(boundQuery: BoundQuery<U>): Match<U>? =
    ms.firstNotNullOfOrNull { it.find(boundQuery) }

  override fun validate(): ValidationResultEnum =
    ms.fold(ValidationResultEnum.Valid) { res, next ->
      res.and { next.validate() }
    }

  override fun observableSubmatches(): Sequence<Match<*>> =
    ms.asSequence().flatMap { it.observableSubmatches() }
}

private class BasedMatch<T>(
  override val value: T,
  val base: Match<*>
) : Match<T> {
  val cachedHash = hash(value, base)

  override fun equals(other: Any?): Boolean =
    other is BasedMatch<*> && other.value == value && other.base == base

  override fun hashCode(): Int =
    cachedHash

  override fun toString(): String =
    "($value, $base)"

  override fun validate(): ValidationResultEnum =
    base.validate()

  override fun observableSubmatches(): Sequence<Match<*>> =
    base.observableSubmatches()
}

private class ValueMatch<T>(override val value: T) : Match<T> {

  val cachedHash = value.hashCode()

  override fun equals(other: Any?): Boolean =
    other is ValueMatch<*> && other.value == value

  override fun hashCode(): Int =
    cachedHash

  override fun toString(): String =
    "$value"

  override fun validate(): ValidationResultEnum =
    ValidationResultEnum.Valid

  override fun observableSubmatches(): Sequence<Match<*>> =
    emptySequence()
}

private class ValidatableMatch<T>(
  override val value: T,
  val base: Match<*>?,
  val validate: () -> ValidationResultEnum
) : Match<T> {

  private val cachedHash = hash(value, base)

  override fun hashCode(): Int =
    cachedHash

  override fun equals(other: Any?): Boolean =
    other is ValidatableMatch<*> && other.value == this.value && other.base == this.base

  override fun <U> find(boundQuery: BoundQuery<U>): Match<U>? =
    base?.find(boundQuery)

  override fun toString(): String =
    if (base != null) "($value $base)" else value.toString()

  override fun validate(): ValidationResultEnum =
    (base?.validate() ?: ValidationResultEnum.Valid).and(validate)

  override fun observableSubmatches(): Sequence<Match<*>> =
    base?.observableSubmatches() ?: emptySequence()
}

private data class NonValidatableMatch<T>(override val value: T) : Match<T> {
  val cachedHash = value.hashCode()

  override fun equals(other: Any?): Boolean =
    other is NonValidatableMatch<*> && other.value == value

  override fun hashCode(): Int =
    cachedHash

  override fun validate(): ValidationResultEnum =
    ValidationResultEnum.Inconclusive

  override fun observableSubmatches(): Sequence<Match<*>> =
    emptySequence()
}

private class ValidatableIntMatch(
  override val value: Int,
  val base: Match<*>?,
  val validate: () -> ValidationResultEnum
) : Match<Int> {

  override fun hashCode(): Int =
    hash(value, base)

  override fun equals(other: Any?): Boolean =
    other is ValidatableIntMatch && other.value == value && other.base == base

  override fun <U> find(boundQuery: BoundQuery<U>): Match<U>? =
    base?.find(boundQuery)

  override fun toString(): String =
    if (base != null) "($value $base)" else value.toString()

  override fun validate(): ValidationResultEnum =
    (base?.validate() ?: ValidationResultEnum.Valid).and(validate)

  override fun observableSubmatches(): Sequence<Match<*>> =
    base?.observableSubmatches() ?: emptySequence()
}

private data class Binding<T>(
  val key: Any,
  val match: Match<T>
) : Match<T> {
  override val value: T get() = match.value

  override fun validate(): ValidationResultEnum = match.validate()

  override fun observableSubmatches(): Sequence<Match<*>> =
    match.observableSubmatches()

  @Suppress("UNCHECKED_CAST")
  override fun <U> find(boundQuery: BoundQuery<U>): Match<U>? =
    (match.takeIf { key == boundQuery.key } as Match<U>?) ?: match.find(boundQuery)
}
