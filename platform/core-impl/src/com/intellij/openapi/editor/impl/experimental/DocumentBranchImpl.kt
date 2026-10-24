// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentModState
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.ex.experimental.DocMerge
import com.intellij.openapi.editor.ex.experimental.EventGraph
import com.intellij.openapi.editor.ex.experimental.Version
import com.intellij.openapi.editor.impl.DocumentTextImpl
import com.intellij.openapi.editor.impl.RangeModState
import com.intellij.openapi.editor.impl.isMove
import com.intellij.openapi.editor.impl.isMovedTextApart
import java.util.Collections

/**
 * See [DocumentBranch].
 *
 * The value holds the [graph] that records the history, the [agent] that authors new events, the
 * [text] that holds the materialized text, and the [modState] of that text. [text] returns the
 * materialized text as is, so the text and the line data behave exactly like [DocumentTextImpl]. A
 * local [applyOp] appends events at the graph's frontier and edits [text] directly. The Eg-walker
 * replay runs only inside [merge], and in the ops of a fast-forward.
 *
 * A merge with concurrent history replays only the region above the common ancestor, which is the
 * partial replay of the paper. One placeholder item stands in for the older document, and it splits
 * only where an op needs it. The new units apply to [text] as ordinary [DocumentOp.Text]s. Those
 * ops are the op stream of [mergeWithOps]. The merge cost depends on the size of the change and of
 * the concurrent region, not on the size of either history.
 *
 * Prototype limits, deliberate:
 * - A keystroke extends the newest run when it continues it (see [EventGraph.append]), but a
 *   backspace never does. Each backspace therefore still costs a run of its own.
 */
internal class DocumentBranchImpl private constructor(
  private val text: DocumentText,
  private val modState: BranchModState,
  private val agent: Agent,
  private val graph: EventGraphImpl,
) : DocumentBranch {

  init {
    // A foreign agent would pass until the first edit, and fail there.
    AgentImpl.implOf(agent)
  }

  override fun text(): DocumentText {
    return text
  }

  override fun modState(): DocumentModState {
    return modState.resolved()
  }

  override fun version(): Version {
    return graph.version()
  }

  override fun applyOp(op: DocumentOp): DocumentBranch {
    return when (op) {
      is DocumentOp.Insert -> applyInsert(op)
      is DocumentOp.Delete -> applyDelete(op)
      is DocumentOp.ModStamp -> applyMetadata(op)
      is DocumentOp.CleanLines -> applyMetadata(op)
    }
  }

  override fun agent(): Agent {
    return agent
  }

  override fun graph(): EventGraph {
    return graph
  }

  override fun fork(agent: Agent): DocumentBranch {
    return DocumentBranchImpl(
      text = text,
      modState = modState,
      agent = agent,
      graph = graph,
    )
  }

  override fun merge(other: DocumentBranch): DocumentBranch {
    return mergeWithOps(other).branch()
  }

  override fun mergeWithOps(other: DocumentBranch): DocMerge {
    val otherImpl = implOf(other)
    val result = graph.mergeFromImpl(otherImpl.graph)
    if (result.addsNothing()) {
      // The same kind of list as the other outcomes, so a caller sees one behaviour.
      return DocMerge.ready(this, Collections.emptyList())
    }
    val merged = result.graph()
    if (result.isFastForward()) {
      return fastForward(merged, otherImpl.text)
    }
    // A partial replay: the walk covers only the region above the common ancestor. Only the new
    // units reach the sink, which joins them into ordinary ops over the text.
    val sink = replayOnto(merged, text)
    val ops = sink.ops()
    val mergedModState = BranchModState.foldModState(text, modState(), ops)
    val branch = DocumentBranchImpl(
      text = sink.result(),
      modState = BranchModState.ready(mergedModState),
      agent = agent,
      graph = merged,
    )
    return DocMerge.ready(branch, ops)
  }

  /**
   * The text of this branch is the document at its version, so a trim to that version takes it as
   * the base text. A deferred mod state keeps the old graph alive until its first read.
   */
  override fun trimmedTo(safepoint: Version): DocumentBranch {
    val trimmed = graph.trimmedTo(graph.lvVersionOf(safepoint), text)
    if (trimmed === graph) {
      return this
    }
    return DocumentBranchImpl(
      text = text,
      modState = modState,
      agent = agent,
      graph = trimmed,
    )
  }

  override fun newInstance(): DocumentBranch {
    return DocumentBranchImpl(text, modState, agent, graph)
  }

  override fun dumpState(): String {
    val dump = StringBuilder()
    dump.append("intervals:\n")
    val lineCount: Int = text.lineCount()
    for (line in 0..<lineCount) {
      dump
        .append(line)
        .append(": ")
        .append(text.lineStartOffset(line))
        .append("-")
        .append(text.lineEndOffset(line))
        .append(", ")
    }
    if (lineCount > 0) {
      dump.setLength(dump.length - 2)
    }
    return dump.toString()
  }

  /**
   * The fast-forward to [merged], whose text is [mergedText].
   *
   * This branch's history is inside [merged], so the text is ready. The ops would cost a replay of
   * the change, so they wait until a caller asks for them, and the mod state waits for the ops. The
   * lambdas take the text and not the other branch, so they do not keep the other graph alive.
   */
  private fun fastForward(merged: EventGraphImpl, mergedText: DocumentText): DocMerge {
    val replay by lazy(LazyThreadSafetyMode.PUBLICATION) {
      replayFastForward(merged)
    }
    val mergedModState = BranchModState.deferred(text, modState()) {
      replay.ops
    }
    val branch = DocumentBranchImpl(
      text = mergedText,
      modState = mergedModState,
      agent = agent,
      graph = merged,
    )
    return DocMerge.deferred(branch) {
      checkFoldsInto(replay.text, mergedText)
      replay.ops
    }
  }

  private fun applyInsert(op: DocumentOp.Insert): DocumentBranch {
    val fragment = op.fragment()
    if (fragment.isEmpty()) {
      return this
    }
    // The inner text validates the offset before the graph changes.
    val newDocText = text.insert(op.offset(), fragment)
    val newModState = modState().applyOp(text, newDocText, op)
    val recorded = recordedInsert(op, newDocText)
    return DocumentBranchImpl(
      text = newDocText,
      modState = BranchModState.ready(newModState),
      agent = agent,
      graph = appendLocal(recorded),
    )
  }

  private fun applyDelete(op: DocumentOp.Delete): DocumentBranch {
    val length = op.length()
    if (length == 0) {
      return this
    }
    val offset = op.offset()
    val newDocText = text.delete(offset, offset + length)
    val newModState = modState().applyOp(text, newDocText, op)
    val recorded = recordedDelete(op)
    return DocumentBranchImpl(
      text = newDocText,
      modState = BranchModState.ready(newModState),
      agent = agent,
      graph = appendLocal(recorded),
    )
  }

  /**
   * [op] changes only the mod state, so the text and the graph keep their instances.
   */
  private fun applyMetadata(op: DocumentOp): DocumentBranch {
    val current = modState()
    val newModState = current.applyOp(text, text, op)
    if (newModState === current) {
      return this
    }
    return DocumentBranchImpl(
      text = text,
      modState = BranchModState.ready(newModState),
      agent = agent,
      graph = graph,
    )
  }

  /**
   * [op] as the graph records it. A move insert whose move offset does not name its source records as
   * a plain insert. The source sits in [newDocText], the text after the insert.
   */
  private fun recordedInsert(op: DocumentOp.Insert, newDocText: DocumentText): DocumentOp.Insert {
    val fragment = op.fragment()
    if (!op.isMove() || isValidMove(op, newDocText, fragment)) {
      return op
    }
    return DocumentOp.insertOp(op.offset(), fragment)
  }

  /**
   * [op] as the graph records it. A move delete whose move offset does not name its copy records as a
   * plain delete. The copy sits in the text before the delete.
   */
  private fun recordedDelete(op: DocumentOp.Delete): DocumentOp.Delete {
    if (!op.isMove()) {
      return op
    }
    val start = op.offset()
    val end = start + op.length()
    val deleted = text.chars().subSequence(start, end)
    if (isValidMove(op, text, deleted)) {
      return op
    }
    return DocumentOp.deleteOp(start, op.length())
  }

  /**
   * The graph with [op] appended at its frontier, under the next free seq of [agent].
   *
   * The graph is the only record of that seq. A merge can bring in units of [agent] that this value
   * never saw, for example from a descendant of it. A seq kept beside the graph would then name a
   * unit that already exists.
   */
  private fun appendLocal(op: DocumentOp.Text): EventGraphImpl {
    return graph.appendAtVersion(EventImpl(agent, graph.nextSeqFor(agent), op))
  }

  /**
   * [start], which holds the text of this branch, with everything that [merged] holds beyond this
   * branch applied, in a sink that recorded the ops. [merged] must come from a merge into this
   * branch, so the lvs of this branch name the same units there.
   */
  private fun replayOnto(merged: EventGraphImpl, start: DocumentText): BatchingSink {
    val sink = BatchingSink(start)
    EgWalkerReplay.mergeInto(merged, graph.lvVersion(), sink)
    return sink
  }

  /**
   * The replay of a fast-forward to [merged]. It walks only the units that [merged] holds beyond this
   * branch, so it costs the size of the change.
   *
   * The replay starts from a text without line data, because nothing reads the line data of its
   * result. With line data, each op would copy the line arrays of the whole document.
   */
  private fun replayFastForward(merged: EventGraphImpl): FastForwardReplay {
    val sink = replayOnto(merged, DocumentText.createText(text.chars()))
    return FastForwardReplay(sink.result(), sink.ops())
  }

  /**
   * Fails unless the ops of a fast-forward build [expected]. Otherwise an editor that applies them
   * would hold another text than the branch, and nothing would say so. It happens when two branches
   * gave one event id to two operations inside a shared range, where the id check does not sample.
   */
  private fun checkFoldsInto(replayed: DocumentText, expected: DocumentText) {
    require(replayed.chars().contentEquals(expected.chars())) {
      "The ops of a fast-forward build another text than the merged text. Two branches gave one event id " +
      "to two operations, and the id check of the merge did not sample it."
    }
  }

  override fun toString(): String {
    val id = Integer.toHexString(System.identityHashCode(this))
    val content = "agent=$agent, events=${graph.size()}, length=${text.length()}, modState=$modState"
    return "DocumentBranch@$id{$content}"
  }

  companion object {
    /**
     * A branch at the root of a new graph. The base text of the graph has the characters of the
     * branch text, and the two share them.
     */
    fun create(chars: CharSequence, agent: Agent): DocumentBranchImpl {
      val text = DocumentText.createText(chars)
      return DocumentBranchImpl(
        text = text,
        modState = BranchModState.ready(RangeModState.create(text)),
        agent = agent,
        graph = EventGraphImpl.startingFrom(text),
      )
    }

    private fun implOf(branch: DocumentBranch): DocumentBranchImpl {
      require(branch is DocumentBranchImpl) {
        "Foreign DocumentBranch implementation: ${branch.javaClass.name}"
      }
      return branch
    }

    /**
     * Whether [text] holds [moved] at the move offset of [op], apart from the text that [op] changes. A
     * wrong move offset would move markers onto other text in every branch that merges the op.
     */
    private fun isValidMove(op: DocumentOp.Text, text: DocumentText, moved: CharSequence): Boolean {
      val moveOffset = op.moveOffset()
      val fitsText = moveOffset >= 0 && moved.length <= text.length() - moveOffset
      if (!fitsText || !op.isMovedTextApart()) {
        return false
      }
      val atMoveOffset = text.chars().subSequence(moveOffset, moveOffset + moved.length)
      return atMoveOffset.contentEquals(moved)
    }
  }
}

/**
 * The text and the ops that a replay of a fast-forward builds. The mod state reads only the ops, and an
 * editor also needs the check that the ops build the merged text.
 */
private class FastForwardReplay(val text: DocumentText, val ops: List<DocumentOp.Text>)
