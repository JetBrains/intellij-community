// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.Event
import com.intellij.openapi.editor.ex.experimental.EventGraph
import com.intellij.openapi.editor.impl.isMove

/**
 * One stored run: an [Event] plus its links into the graph. The run covers the lvs
 * `[lvStart, lvEnd())`, one per unit of the event.
 *
 * The run owns the arithmetic between an lv and the unit it names. A caller asks the run
 * about an lv and never subtracts [lvStart] itself, and every query checks that the run holds
 * the lv it gets.
 *
 * The event is an [EventImpl], so a stored run can only hold an event that checked itself.
 */
internal class StoredRun(
  val event: EventImpl,
  val lvStart: LV,
  private val parents: Frontier,
) {
  /**
   * The lv after the last unit.
   */
  fun lvEnd(): LV {
    return lvStart + event.length()
  }

  /**
   * The index inside this run of the unit [lv], which this run must hold.
   */
  fun unitIndexOf(lv: LV): Int {
    require(lv in lvStart until lvEnd()) {
      "The lv $lv is outside the run $this"
    }
    return lv - lvStart
  }

  /**
   * The first seq after the last unit.
   */
  fun endSeq(): Long {
    return event.seq() + event.length()
  }

  /**
   * Whether this run holds the unit ([agent], [seq]).
   */
  fun holdsUnit(agent: Agent, seq: Long): Boolean {
    return agent == event.agent() && seq in event.seq() until endSeq()
  }

  /**
   * The lv of the unit [seq], which this run must hold.
   */
  fun lvOfSeq(seq: Long): LV {
    return lvStart + unitIndexOfSeq(seq)
  }

  /**
   * The index inside this run of the unit [seq], which this run must hold. The index is below the
   * length of the run, so it fits an [Int].
   */
  private fun unitIndexOfSeq(seq: Long): Int {
    require(seq >= event.seq() && seq < endSeq()) {
      "The seq $seq is outside the run $this"
    }
    return (seq - event.seq()).toInt()
  }

  /**
   * Whether [lv] is at or after the first unit. The run search uses this.
   */
  fun startsAtOrBefore(lv: LV): Boolean {
    return lvStart <= lv
  }

  /**
   * Whether the event of this run deletes.
   */
  fun isDelete(): Boolean {
    return event.op() is DocumentOp.Delete
  }

  /**
   * Whether the op of this run is half of a text move.
   */
  fun isMove(): Boolean {
    return event.op().isMove()
  }

  /**
   * The move offset of the op of this run. See [DocumentOp.Text.moveOffset].
   */
  fun moveOffset(): Int {
    return event.op().moveOffset()
  }

  /**
   * The distance from the offset of the op of this run to its move offset. It is 0 for an op that
   * moves nothing, wherever the run starts.
   */
  fun moveDistance(): Int {
    val op = event.op()
    return op.moveOffset() - op.offset()
  }

  /**
   * Whether the [count] units from [lv] are the whole run.
   */
  fun isWholeRun(lv: LV, count: Int): Boolean {
    return lv == lvStart && count == event.length()
  }

  /**
   * The parents of the unit [lv]. The first unit keeps the parents the append recorded;
   * every later unit has the one implicit parent `lv - 1`.
   */
  fun parentsOf(lv: LV): Frontier {
    return if (unitIndexOf(lv) == 0) {
      parents
    } else {
      intArrayOf(lv - 1)
    }
  }

  /**
   * The parents of the first unit, which are the parents of the whole run.
   */
  fun runParents(): Frontier {
    return parents
  }

  /**
   * The offset that the unit [lv] edits, in its own parent version.
   */
  fun offsetAt(lv: LV): Int {
    return event.offsetOfUnit(unitIndexOf(lv))
  }

  /**
   * The seq that names the unit [lv].
   */
  fun seqAt(lv: LV): Long {
    return event.seq() + unitIndexOf(lv)
  }

  /**
   * The character that the unit [lv] inserts. The run must be an insert.
   */
  fun charAt(lv: LV): Char {
    return insertOp(lv).fragment()[unitIndexOf(lv)]
  }

  /**
   * The characters that the [count] units from [lv] insert. The run must be an insert, and
   * it must cover the whole span.
   */
  fun fragmentFrom(lv: LV, count: Int): CharSequence {
    val from = unitIndexOf(lv)
    require(count in 1..event.length() - from) {
      "The span of $count units from $lv is outside the run $this"
    }
    return insertOp(lv).fragment().subSequence(from, from + count)
  }

  /**
   * This run extended by [next], or `null` when [next] does not continue it. The reference
   * implementation makes the same test for its graph entries in `tryAppendEntries`, and extends
   * the entry in place. A run is immutable, so this returns a new one.
   *
   * A later unit of a run has two properties that nothing stores. Its one parent is the unit
   * before it, and it edits at the offset that [Event.offsetOfUnit] gives it. So [next] must
   * have both. [parents] must name only the last unit of this run. The op of [next] must start
   * where the next unit of this run would edit. The id must continue too, because a run holds
   * one id range.
   *
   * An insert run grows only up to [EventGraph.MAX_COALESCED_INSERT] characters, because each
   * extension copies the fragment.
   */
  fun tryAppend(next: EventImpl, parents: LvVersion): StoredRun? {
    val continuesId = next.agent() == event.agent() && next.seq() == endSeq()
    if (!isOnlyParent(parents) || !continuesId) {
      return null
    }
    val nextOp = next.op()
    // A half of a move keeps a run of its own, because a joined op would lose the move offset.
    if (isMove() || nextOp.isMove()) {
      return null
    }
    val joined = when (val op = event.op()) {
      is DocumentOp.Insert -> {
        if (nextOp !is DocumentOp.Insert || !continuesInsert(op, nextOp)) {
          return null
        }
        DocumentOp.insertOp(op.offset(), op.fragment().toString() + nextOp.fragment())
      }
      is DocumentOp.Delete -> {
        if (nextOp !is DocumentOp.Delete || !continuesDelete(op, nextOp)) {
          return null
        }
        DocumentOp.deleteOp(op.offset(), op.length() + nextOp.length())
      }
    }
    return StoredRun(EventImpl(event.agent(), event.seq(), joined), lvStart, this.parents)
  }

  /**
   * Whether [parents] names the last unit of this run and nothing else.
   */
  private fun isOnlyParent(parents: LvVersion): Boolean {
    val lvs = parents.lvs
    return lvs.size == 1 && lvs[0] == lvEnd() - 1
  }

  /**
   * An insert walks forward, so [next] continues at the end of [op], within the length limit.
   */
  private fun continuesInsert(op: DocumentOp.Insert, next: DocumentOp.Insert): Boolean {
    return next.offset() == op.offset() + op.length() &&
           next.length() <= EventGraph.MAX_COALESCED_INSERT - op.length()
  }

  /**
   * A delete stays in place, so [next] continues at the offset of [op]. The joined delete must
   * still fit the offset space that an event checks, or the append would fail where a new run
   * succeeds.
   */
  private fun continuesDelete(op: DocumentOp.Delete, next: DocumentOp.Delete): Boolean {
    return next.offset() == op.offset() &&
           next.length() <= Int.MAX_VALUE - op.offset() - op.length()
  }

  /**
   * The insert op of this run, for a caller that reads its content.
   */
  private fun insertOp(lv: LV): DocumentOp.Insert {
    val op = event.op()
    require(op is DocumentOp.Insert) {
      "The lv $lv is not an insert"
    }
    return op
  }

  override fun toString(): String {
    return "run[$lvStart..${lvEnd() - 1}] $event"
  }
}
