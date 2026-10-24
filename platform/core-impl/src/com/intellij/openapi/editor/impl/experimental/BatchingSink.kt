// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.applyOp
import com.intellij.openapi.editor.ex.experimental.DocMerge
import java.util.Collections

/**
 * Turns the reports of a merge into ops, applies them to the text, and records them. The recorded
 * ops are the op stream of [DocMerge.ops].
 *
 * The sink is a state machine over one pending op: [NoPending], [PendingInsert], [PendingDelete], or
 * [PendingMove]. A report that continues the pending op joins it:
 * - an insert at the end of a pending insert extends its fragment;
 * - a delete that starts inside a pending insert and ends at its end shortens the fragment. The
 *   new side deleted text that it inserted itself, so no op brings that text at all;
 * - a delete at the position of a pending delete extends it, as the Delete key does;
 * - a delete that ends at the position of a pending delete extends it backwards, as a backspace
 *   does;
 * - a move delete at the source of a pending move removes the next piece of the source. The last
 *   piece emits the pair with move offsets, when the source holds the fragment.
 *
 * Any other report flushes the pending op and starts a new one. A flushed move leaves as plain ops,
 * and a move delete that continues no move acts as a delete. A move op never joins a neighbour.
 *
 * Each join costs O(1), because it never moves the characters of the pending fragment. The walk
 * reports one span per run, so the joins pay off across runs. Two runs that land side by side
 * arrive as two reports and leave as one op.
 *
 * The sink is single use, and one merge owns it. [result] and [ops] finish it, and a report after
 * that fails.
 */
internal class BatchingSink(
  private var updated: DocumentText,
) : EgWalkerReplay.Sink {
  private var pending: Pending = NoPending
  private val applied = ArrayList<DocumentOp.Text>()
  private var finished = false

  override fun insert(effectPos: Int, fragment: CharSequence) {
    checkOpen()
    checkInsert(effectPos, fragment)
    val joined = when (val current = pending) {
      is PendingInsert -> current.joinInsert(effectPos, fragment)
      NoPending, is PendingDelete, is PendingMove -> false
    }
    if (!joined) {
      start(PendingInsert(effectPos, fragment))
    }
  }

  override fun delete(effectPos: Int, count: Int) {
    checkOpen()
    checkDelete(effectPos, count)
    val joined = when (val current = pending) {
      is PendingInsert -> shortens(current, effectPos, count)
      is PendingDelete -> current.joinDelete(effectPos, count)
      NoPending, is PendingMove -> false
    }
    if (!joined) {
      start(PendingDelete(effectPos, count))
    }
  }

  override fun moveInsert(effectPos: Int, fragment: CharSequence, sourceEffectPos: Int) {
    checkOpen()
    checkInsert(effectPos, fragment)
    start(PendingMove(effectPos, fragment.toString(), sourceEffectPos))
  }

  override fun moveDelete(effectPos: Int, count: Int, copyEffectPos: Int) {
    checkOpen()
    val move = when (val current = pending) {
      is PendingMove -> current
      NoPending, is PendingInsert, is PendingDelete -> null
    }
    if (move == null || !move.continuesWith(effectPos, count, copyEffectPos)) {
      delete(effectPos, count)
      return
    }
    checkDelete(effectPos, count)
    move.remove(count)
    if (move.isComplete()) {
      flush()
    }
  }

  /**
   * The text with every report applied. This finishes the sink.
   */
  fun result(): DocumentText {
    finish()
    return updated
  }

  /**
   * The ops that [result] applied to the text, in order. This finishes the sink.
   */
  fun ops(): List<DocumentOp.Text> {
    finish()
    return Collections.unmodifiableList(applied)
  }

  /**
   * The text length so far, the op that still waits for its neighbour, and the ops so far.
   */
  override fun toString(): String {
    val state = if (finished) "finished" else "open"
    return "BatchingSink(length=${updated.length()}, pending=$pending, ops=${applied.size}, $state)"
  }

  private fun finish() {
    if (!finished) {
      flush()
      finished = true
    }
  }

  /**
   * Flushes the pending op, and makes [next] the pending one.
   */
  private fun start(next: Pending) {
    flush()
    pending = next
  }

  private fun flush() {
    // The effect version IS the text this sink builds, so its position is the op's offset.
    when (val current = pending) {
      NoPending -> Unit
      is PendingInsert -> emit(current.op())
      is PendingDelete -> emit(current.op())
      is PendingMove -> flushMove(current)
    }
    pending = NoPending
  }

  /**
   * Emits [move] as a pair with move offsets when its delete removed the whole source, and the source
   * holds the fragment. Otherwise it emits plain ops.
   */
  private fun flushMove(move: PendingMove) {
    if (move.isComplete() && sourceHoldsFragment(move)) {
      emit(move.insertOp())
      emit(move.deleteOp())
    } else {
      for (op in move.plainOps()) {
        emit(op)
      }
    }
  }

  /**
   * Whether the source of [move] holds its fragment in the text before the insert. The check keeps a
   * faulty event from moving markers onto other text.
   */
  private fun sourceHoldsFragment(move: PendingMove): Boolean {
    val sourceStart = move.sourceStartBeforeInsert()
    if (sourceStart < 0) {
      return false
    }
    val fragment = move.fragment()
    val source = updated.chars().subSequence(sourceStart, sourceStart + fragment.length)
    return source.contentEquals(fragment)
  }

  /**
   * Shortens the pending [insert] when the delete removes its end. An insert that loses its whole
   * fragment leaves no op.
   */
  private fun shortens(insert: PendingInsert, effectPos: Int, count: Int): Boolean {
    if (!insert.cutEnd(effectPos, count)) {
      return false
    }
    if (insert.isEmpty()) {
      pending = NoPending
    }
    return true
  }

  private fun emit(op: DocumentOp.Text) {
    updated = updated.applyOp(op)
    applied.add(op)
  }

  /**
   * The length of the text with every report so far applied, the pending op included.
   */
  private fun currentLength(): Int {
    return updated.length() + pending.lengthDelta()
  }

  private fun checkOpen() {
    require(!finished) {
      "A report arrived after the sink finished"
    }
  }

  /**
   * Fails unless the insert brings text at a position of the current text. The text is what the
   * merge builds, so a report outside it means a faulty event. This check names the fault, before a
   * flush fails with no name.
   */
  private fun checkInsert(effectPos: Int, fragment: CharSequence) {
    require(fragment.isNotEmpty()) {
      "An empty insert at $effectPos"
    }
    require(effectPos in 0..currentLength()) {
      "The insert at $effectPos is outside the text of length ${currentLength()}"
    }
  }

  /**
   * Fails unless the delete removes at least one character, all inside the current text.
   */
  private fun checkDelete(effectPos: Int, count: Int) {
    require(count >= 1) {
      "The delete count is not positive: $count"
    }
    require(effectPos >= 0 && count <= currentLength() - effectPos) {
      "The delete of $count at $effectPos is outside the text of length ${currentLength()}"
    }
  }

  /**
   * The op that waits for a neighbour to join it.
   */
  private sealed interface Pending {
    /**
     * How much this op changes the length of the text.
     */
    fun lengthDelta(): Int
  }

  private object NoPending : Pending {
    override fun lengthDelta(): Int = 0

    override fun toString(): String {
      return "nothing"
    }
  }

  /**
   * An insert at [start]. Its fragment grows and shrinks in place, so a join never moves the
   * characters.
   */
  private class PendingInsert(private val start: Int, fragment: CharSequence) : Pending {
    private val fragment = StringBuilder(fragment)

    override fun lengthDelta(): Int = fragment.length

    /**
     * Appends [more] when it starts at the end of this insert.
     */
    fun joinInsert(effectPos: Int, more: CharSequence): Boolean {
      if (effectPos != end()) {
        return false
      }
      fragment.append(more)
      return true
    }

    /**
     * Cuts the end of the fragment when a delete of [count] at [effectPos] starts inside it and ends
     * at its end.
     */
    fun cutEnd(effectPos: Int, count: Int): Boolean {
      if (effectPos < start || effectPos + count != end()) {
        return false
      }
      fragment.setLength(effectPos - start)
      return true
    }

    fun isEmpty(): Boolean {
      return fragment.isEmpty()
    }

    fun op(): DocumentOp.Insert {
      return DocumentOp.insertOp(start, fragment.toString())
    }

    private fun end(): Int {
      return start + fragment.length
    }

    override fun toString(): String {
      return "insert at $start of ${fragment.quotedForMessage()}"
    }
  }

  /**
   * A delete of [count] characters at [start].
   */
  private class PendingDelete(private var start: Int, private var count: Int) : Pending {
    override fun lengthDelta(): Int = -count

    /**
     * Joins a delete of [more] at [effectPos]. It joins at the same position, as the Delete key does,
     * or when it ends at this position, as a backspace does.
     */
    fun joinDelete(effectPos: Int, more: Int): Boolean {
      if (effectPos == start) {
        count += more
        return true
      }
      if (effectPos + more == start) {
        start = effectPos
        count += more
        return true
      }
      return false
    }

    fun op(): DocumentOp.Delete {
      return DocumentOp.deleteOp(start, count)
    }

    override fun toString(): String {
      return "delete of $count at $start"
    }
  }

  /**
   * A move insert of [fragment] at [copyStart] that waits for its delete. [sourceStart] is the source
   * in the text after the insert. The delete removed [removed] characters of the source so far. That
   * stays below the fragment length while the move waits, because the last piece flushes the move.
   */
  private class PendingMove(
    private val copyStart: Int,
    private val fragment: String,
    private val sourceStart: Int,
  ) : Pending {
    private var removed = 0

    override fun lengthDelta(): Int = fragment.length - removed

    fun fragment(): String = fragment

    /**
     * Whether a delete of [count] at [effectPos] continues this move: it removes the source, names
     * the copy, and removes no more than the move copied.
     */
    fun continuesWith(effectPos: Int, count: Int, copyEffectPos: Int): Boolean {
      return effectPos == sourceStart &&
             copyEffectPos == copyStart &&
             count <= fragment.length - removed
    }

    fun remove(count: Int) {
      removed += count
    }

    fun isComplete(): Boolean = removed == fragment.length

    /**
     * The start of the source in the text before the insert, or -1 when the source overlaps the copy.
     */
    fun sourceStartBeforeInsert(): Int {
      val length = fragment.length
      if (sourceStart >= copyStart + length) {
        return sourceStart - length
      }
      if (sourceStart + length <= copyStart) {
        return sourceStart
      }
      return -1
    }

    fun insertOp(): DocumentOp.Insert {
      return DocumentOp.insertOp(copyStart, fragment, sourceStart)
    }

    fun deleteOp(): DocumentOp.Delete {
      return DocumentOp.deleteOp(sourceStart, removed, copyStart)
    }

    /**
     * The insert and the removed part of the source, as plain ops.
     */
    fun plainOps(): List<DocumentOp.Text> {
      val insert = DocumentOp.insertOp(copyStart, fragment)
      if (removed == 0) {
        return listOf(insert)
      }
      val delete = DocumentOp.deleteOp(sourceStart, removed)
      return listOf(insert, delete)
    }

    override fun toString(): String {
      val quoted = fragment.quotedForMessage()
      return "move of $quoted to $copyStart from $sourceStart, $removed deleted"
    }
  }
}
