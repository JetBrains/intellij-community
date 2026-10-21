// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.experimental.EventImpl

/**
 * One node of the Eg-walker event graph: a run of units with one id range.
 *
 * A UNIT is one single-character operation: one character that an insert adds, or one that a
 * delete removes. Every unit has its own event id, ([agent], seq). An event is an identity plus the
 * change that the identity names. The identity is ([agent], [seq]) to ([agent], `seq + length - 1`),
 * and the change is [op].
 *
 * An event is NOT a [DocumentOp.Text], so [DocumentText.applyOp] will not take one. The offset of
 * [op] indexes the document as it was in the PARENT VERSION. An old event applied to a document at
 * another version means nothing.
 *
 * One event covers [length] units. The parents of the first unit live in [EventGraph], and the
 * parent of every later unit is the unit before it.
 *
 * An event is immutable: a merge never rewrites it.
 */
interface Event {
  fun agent(): Agent

  /**
   * The seq of the first unit. The run consumes the seqs `[seq, seq + length)`.
   */
  fun seq(): Long

  /**
   * The change this event records, against the document of its parent version.
   */
  fun op(): DocumentOp.Text

  /**
   * The number of units in this run. At least 1.
   */
  fun length(): Int

  /**
   * The offset that the unit [index] of this run edits, in the document at the parents of that
   * unit. [index] must be in `[0, length)`. An insert unit edits at `offset + index`, and a delete
   * unit edits at `offset`.
   */
  fun offsetOfUnit(index: Int): Int

  /**
   * The part of this run from the unit [units] onward, as an event of its own. [units] must be in
   * `[0, length)`, and a zero [units] returns this event. The suffix of a move is a plain op.
   */
  fun suffixFrom(units: Int): Event

  companion object {
    /**
     * An event that records [op] under the id ([agent], [seq]). [op] must come from
     * [DocumentOp.insertOp] or [DocumentOp.deleteOp], because the event keeps it as it is.
     * [seq] must be at least 0, and [seq] plus the length of [op] must not exceed `Long.MAX_VALUE`.
     */
    @JvmStatic
    fun create(agent: Agent, seq: Long, op: DocumentOp.Text): Event {
      return EventImpl(agent, seq, op)
    }

    @JvmStatic
    fun createInsert(
      agent: Agent,
      seq: Long,
      offset: Int,
      fragment: CharSequence,
    ): Event {
      return create(agent, seq, DocumentOp.insertOp(offset, fragment))
    }

    @JvmStatic
    fun createDelete(
      agent: Agent,
      seq: Long,
      offset: Int,
      length: Int,
    ): Event {
      return create(agent, seq, DocumentOp.deleteOp(offset, length))
    }
  }
}
