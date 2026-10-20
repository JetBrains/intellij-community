// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.Event
import com.intellij.openapi.editor.impl.DeleteOpImpl
import com.intellij.openapi.editor.impl.InsertOpImpl
import com.intellij.openapi.editor.impl.isMove
import com.intellij.openapi.editor.impl.isMovedTextApart

internal class EventImpl(
    private val agent: Agent,
    private val seq: Long,
    private val op: DocumentOp.Text,
) : Event {

  init {
    // The id order of the graph compares agents, and only AgentImpl knows how.
    AgentImpl.implOf(agent)
    checkKnownOp(op)
    val offset = op.offset()
    val length = op.length()
    checkNotNegative(seq, offset)
    checkLength(length)
    checkIdSpace(seq, length)
    checkOffsetSpace(offset, length)
    checkMove(op)
  }

  override fun agent(): Agent = agent
  override fun seq(): Long = seq
  override fun op(): DocumentOp.Text = op
  override fun length(): Int = op.length()

  override fun offsetOfUnit(index: Int): Int {
    checkUnitIndex(index)
    return when (op) {
      is DocumentOp.Insert -> op.offset() + index
      is DocumentOp.Delete -> op.offset()
    }
  }

  override fun suffixFrom(units: Int): EventImpl {
    checkUnitIndex(units)
    if (units == 0) {
      return this
    }
    return EventImpl(agent, seq + units, suffixOp(units))
  }

  /**
   * The part of [op] from the unit [units] onward. A delete keeps its offset; see [offsetOfUnit].
   * A part of a move moves nothing, so the suffix is a plain op.
   */
  private fun suffixOp(units: Int): DocumentOp.Text {
    return when (op) {
      is DocumentOp.Insert -> {
        val fragment = op.fragment()
        DocumentOp.insertOp(op.offset() + units, fragment.subSequence(units, fragment.length))
      }
      is DocumentOp.Delete -> {
        DocumentOp.deleteOp(op.offset(), op.length() - units)
      }
    }
  }

  /**
   * Fails unless [index] names a unit of this run. A suffix from the end would be empty.
   */
  private fun checkUnitIndex(index: Int) {
    require(index in 0 until length()) {
      "The unit index $index is outside the run of length ${length()}"
    }
  }

  override fun toString(): String {
    return "$op by $agent, seq $seq"
  }

  companion object {
    /**
     * [event] as the one implementation the graph trusts. The graph keeps an event forever, and the
     * checks of the constructor are what make its length and its id range safe to store.
     */
    fun implOf(event: Event): EventImpl {
      require(event is EventImpl) {
        "Foreign Event implementation: ${event.javaClass.name}"
      }
      return event
    }
  }
}

private fun checkKnownOp(op: DocumentOp.Text) {
  require(op is InsertOpImpl || op is DeleteOpImpl) {
    "Foreign DocumentTextOp implementation: ${op.javaClass.name}. An event keeps its op forever, " +
    "so the op must come from DocumentOp.insertOp or DocumentOp.deleteOp."
  }
}

private fun checkNotNegative(seq: Long, offset: Int) {
  require(seq >= 0) {
    "Negative seq: $seq"
  }
  require(offset >= 0) {
    "Negative offset: $offset"
  }
}

/**
 * An empty op is a legal no-op, but an empty event would own no id and name nothing.
 */
private fun checkLength(length: Int) {
  require(length >= 1) {
    "The event length is not positive: $length"
  }
}

private fun checkIdSpace(seq: Long, length: Int) {
  require(length <= Long.MAX_VALUE - seq) {
    "The seq space overflows: seq $seq + length $length"
  }
}

private fun checkOffsetSpace(offset: Int, length: Int) {
  require(length <= Int.MAX_VALUE - offset) {
    "The offset space overflows: offset $offset + length $length"
  }
}

/**
 * Fails unless the moved text of a move op lies apart from the text that the op changes. The event
 * cannot see the document, so the content is the job of the caller.
 */
private fun checkMove(op: DocumentOp.Text) {
  if (!op.isMove()) {
    return
  }
  val moveOffset = op.moveOffset()
  require(moveOffset >= 0) {
    "Negative move offset: $moveOffset"
  }
  checkOffsetSpace(moveOffset, op.length())
  require(op.isMovedTextApart()) {
    "The moved text at $moveOffset overlaps the text that $op changes"
  }
}
