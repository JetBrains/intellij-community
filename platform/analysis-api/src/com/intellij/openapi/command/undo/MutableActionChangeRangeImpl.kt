// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.undo

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class MutableActionChangeRangeImpl(private var internalState: ImmutableActionChangeRange) : MutableActionChangeRange {
  override var state: ImmutableActionChangeRange
    get() = internalState
    set(value) {
      require(value.id == internalState.id)
      internalState = value
    }

  override val originalTimestamp: Int = internalState.timestamp

  override fun asInverted(): MutableActionChangeRange {
    return Inverted(this)
  }

  override val offset: Int get() = state.offset
  override val oldLength: Int get() = state.oldLength
  override val newLength: Int get() = state.newLength
  override val oldDocumentLength: Int get() = state.oldDocumentLength
  override val newDocumentLength: Int get() = state.newDocumentLength
  override val id: Int get() = state.id
  override val timestamp: Int get() = state.timestamp
  override fun toImmutable(invalidate: Boolean): ImmutableActionChangeRange = state.toImmutable(invalidate)

  override fun toString(): String {
    return "Mutable ${state}"
  }
}

private class Inverted(private val original: MutableActionChangeRange) : MutableActionChangeRange {
  private var invertedInternalState = original.state.asInverted()
  override fun asInverted(): MutableActionChangeRange = original

  override var state: ImmutableActionChangeRange
    get() = invertedInternalState
    set(value) {
      original.state = value.asInverted()
      invertedInternalState = value
    }

  override val originalTimestamp: Int
    get() = original.originalTimestamp

  override val offset: Int get() = state.offset
  override val oldLength: Int get() = state.oldLength
  override val newLength: Int get() = state.newLength
  override val oldDocumentLength: Int get() = state.oldDocumentLength
  override val newDocumentLength: Int get() = state.newDocumentLength
  override val id: Int get() = state.id
  override val timestamp: Int get() = state.timestamp
  override fun toImmutable(invalidate: Boolean): ImmutableActionChangeRange = state.toImmutable(invalidate)

  override fun toString(): String {
    return "Mutable ${state}"
  }
}
