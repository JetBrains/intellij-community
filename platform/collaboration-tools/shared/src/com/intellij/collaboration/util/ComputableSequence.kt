// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.collaboration.util.SequenceComputer.ComputationOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface SequenceComputer<T> {
  /**
   * Tries to compute the next item.
   *
   * @return [ComputationOutcome.Item] holding the computed value,
   * or [ComputationOutcome.Done] if the sequence is finished
   */
  suspend fun computeNext(): ComputationOutcome<T>

  sealed interface ComputationOutcome<out T> {
    data class Item<T>(val value: T) : ComputationOutcome<T>
    object Done : ComputationOutcome<Nothing>
  }

  companion object {
    /**
     * Builds a computer that walks a pointer [PTR]: [compute] maps the current pointer to the next value and the pointer to
     * use afterwards, or `null` when the value just produced is the last one.
     *
     * The produced items are [SequenceItem]s carrying [SequenceItem.isLast] (derived from that trailing pointer), so a
     * consumer may finish as soon as the last value is produced instead of asking for one more item only to get
     * [ComputationOutcome.Done].
     */
    fun <PTR : Any, T> byPointer(initialPointer: PTR, compute: suspend (PTR) -> Pair<T, PTR?>): SequenceComputer<SequenceItem<T>> =
      object : SequenceComputer<SequenceItem<T>> {
        private var pointer: PTR? = initialPointer

        override suspend fun computeNext(): ComputationOutcome<SequenceItem<T>> {
          val currentPointer = pointer ?: return ComputationOutcome.Done
          val (item, nextPointer) = compute(currentPointer)
          pointer = nextPointer
          return ComputationOutcome.Item(SequenceItem(item, isLast = nextPointer == null))
        }
      }

    /**
     * Builds a computer that computes the next item using a cursor [C] of the last computed item.
     *
     * For the first request a `null` cursor is passed to [compute]
     * If [compute] returns `null`, the last computed cursor will be used for the next computation
     */
    fun <C : Any, T> byCursor(startCursor: C?, compute: suspend (C?) -> Pair<T, C>?): SequenceComputer<T> =
      object : SequenceComputer<T> {
        private var lastCursor: C? = startCursor

        override suspend fun computeNext(): ComputationOutcome<T> {
          val computed = compute(lastCursor) ?: return ComputationOutcome.Done
          val item = computed.first
          lastCursor = computed.second
          return ComputationOutcome.Item(item)
        }
      }
  }
}

/**
 * Represents a sequence of items where each item is computed upon request ([SequenceComputer.computeNext])
 * Each call to `getComputer` should return a new computer which would compute the same sequence from the beginning.
 */
@ApiStatus.Experimental
fun interface ComputableSequence<T> {
  fun getComputer(): SequenceComputer<T>

  companion object {
    /**
     * Shorthand for [SequenceComputer.byPointer]
     */
    fun <PTR : Any, T> byPointer(initialPointer: PTR, compute: suspend (PTR) -> Pair<T, PTR?>): ComputableSequence<SequenceItem<T>> =
      ComputableSequence {
        SequenceComputer.byPointer(initialPointer, compute)
      }

    /**
     * Shorthand for [SequenceComputer.byCursor]
     */
    fun <C : Any, T> byCursor(startCursor: C?, compute: suspend (C?) -> Pair<T, C>?): ComputableSequence<T> =
      ComputableSequence {
        SequenceComputer.byCursor(startCursor, compute)
      }
  }
}

@ApiStatus.Experimental
fun <T> ComputableSequence<T>.asFlow(): Flow<T> =
  flow {
    val computer = getComputer()
    while (true) {
      when (val outcome = computer.computeNext()) {
        ComputationOutcome.Done -> break
        is ComputationOutcome.Item<T> -> emit(outcome.value)
      }
    }
  }

@ApiStatus.Experimental
suspend fun <T> ComputableSequence<T>.toList(): List<T> = asFlow().toList()

/**
 * Returns a sequence that applies [transform] to every computed item. Each [ComputableSequence.getComputer] of the
 * result maps a fresh computer of the original sequence, so any per-computation state is preserved.
 */
@ApiStatus.Experimental
fun <T, R> ComputableSequence<T>.map(transform: (T) -> R): ComputableSequence<R> =
  ComputableSequence {
    val computer = getComputer()
    object : SequenceComputer<R> {
      override suspend fun computeNext(): ComputationOutcome<R> =
        when (val outcome = computer.computeNext()) {
          ComputationOutcome.Done -> ComputationOutcome.Done
          is ComputationOutcome.Item<T> -> ComputationOutcome.Item(transform(outcome.value))
        }
    }
  }

/**
 * Applies [transform] to the value of every computed [SequenceItem], preserving its [SequenceItem.isLast] flag.
 * Handy for sequences produced by [SequenceComputer.byPointer], whose items already carry the last-item hint.
 */
@ApiStatus.Experimental
fun <T, R> ComputableSequence<SequenceItem<T>>.mapItems(transform: (T) -> R): ComputableSequence<SequenceItem<R>> =
  map { SequenceItem(transform(it.value), it.isLast) }

@ApiStatus.Experimental
fun <T> Sequence<T>.asComputedSequence(): ComputableSequence<T> = ComputableSequence {
  val iterator = iterator()
  object : SequenceComputer<T> {
    override suspend fun computeNext(): ComputationOutcome<T> =
      if (iterator.hasNext()) ComputationOutcome.Item(iterator.next())
      else ComputationOutcome.Done
  }
}
