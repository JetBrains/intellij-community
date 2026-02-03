// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.undo

import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

@ApiStatus.Experimental
class ImmutableActionChangeRange private constructor(
  override val offset: Int,
  override val oldLength: Int,
  override val newLength: Int,
  override val oldDocumentLength: Int,
  override val newDocumentLength: Int,
  override val id: Int,
  override val timestamp: Int,
  val actionReference: WeakReference<AdjustableUndoableAction?>
) : ActionChangeRange {
  companion object {
    private val NullRef = WeakReference<AdjustableUndoableAction?>(null)
    private val idCounter = AtomicInteger(0)
    private val timestampCounter = AtomicInteger(0)

    fun createNew(
      offset: Int,
      oldLength: Int,
      newLength: Int,
      oldDocumentLength: Int,
      newDocumentLength: Int,
      actionReference: AdjustableUndoableAction
    ): ImmutableActionChangeRange {
      val id = idCounter.incrementAndGet()
      return ImmutableActionChangeRange(offset, oldLength, newLength, oldDocumentLength, newDocumentLength, id,
                                        timestampCounter.incrementAndGet(), WeakReference(actionReference))
    }
  }

  fun hasTheSameOrigin(other: ImmutableActionChangeRange): Boolean {
    return id == other.id || id == -other.id
  }

  fun isSymmetricTo(newRange: ImmutableActionChangeRange): Boolean {
    return offset == newRange.offset && oldLength == newRange.newLength && newLength == newRange.oldLength
  }

  private fun toInvalid(): ImmutableActionChangeRange {
    return if (actionReference.get() != null) ImmutableActionChangeRange(offset, oldLength, newLength, oldDocumentLength, newDocumentLength,
                                                                         id, timestamp, NullRef)
    else this
  }

  override fun asInverted(): ImmutableActionChangeRange {
    return ImmutableActionChangeRange(offset, newLength, oldLength, newDocumentLength, oldDocumentLength, -id, timestamp, actionReference)
  }

  override fun toImmutable(invalidate: Boolean): ImmutableActionChangeRange {
    return if (invalidate) toInvalid() else this
  }

  /**
   * Returns a copy with modified ranges and a new [timestamp] if the corresponding change was applied after another one (affecting the same document),
   * or `null` if the move is not possible.
   *
   * This method can be used in conjunction with [ActionChangeRange.asInverted] to swap the order of two independent changes
   * to the same document:
   * ```
   * firstRange.moveAfter(secondRange, true)
   * secondRange.moveAfter(firstRange.asInverted(), false)
   * ```
   *
   * @param preferBefore If it's not clear whether the other range is before or after this one,
   *                     this parameter assumes it's before when set to `true` and after when `false`.
   * @return A copy with modified ranges if the move is possible, or `null` if it is not.
   */
  fun moveAfter(other: ImmutableActionChangeRange, preferBefore: Boolean): ImmutableActionChangeRange? {
    val adjustment = other.newLength - other.oldLength
    if (preferBefore) {
      return moveRight(other, adjustment) ?: moveLeft(other, adjustment)
    }
    else {
      return moveLeft(other, adjustment) ?: moveRight(other, adjustment)
    }
  }

  private fun moveRight(other: ImmutableActionChangeRange, adjustment: Int): ImmutableActionChangeRange? {
    if (other.offset + other.oldLength <= offset) {
      return copyWithNewTimestamp(offset + adjustment, oldDocumentLength + adjustment, newDocumentLength + adjustment)
    }

    return null
  }

  private fun moveLeft(other: ImmutableActionChangeRange, adjustment: Int): ImmutableActionChangeRange? {
    if (other.offset >= offset + newLength) {
      return copyWithNewTimestamp(offset, oldDocumentLength + adjustment, newDocumentLength + adjustment)
    }

    return null
  }

  private fun copyWithNewTimestamp(offset: Int, oldDocumentLength: Int, newDocumentLength: Int): ImmutableActionChangeRange {
    return ImmutableActionChangeRange(offset, oldLength, newLength, oldDocumentLength, newDocumentLength, id,
                                      timestampCounter.incrementAndGet(), actionReference)
  }

  override fun toString(): String {
    val action = actionReference.get()
    val info = if (action == null) "" else " for $action"
    return buildString {
      if (id < 0) // is inverted
        append("Inverted ")
      append("[($offset, $oldLength, $newLength, $oldDocumentLength, $newDocumentLength) $info]")
    }
  }
}