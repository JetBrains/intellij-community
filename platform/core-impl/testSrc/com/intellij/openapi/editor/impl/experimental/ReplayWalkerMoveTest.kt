// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocBranch
import com.intellij.openapi.editor.ex.experimental.Event
import com.intellij.openapi.editor.ex.experimental.EventGraph
import com.intellij.openapi.editor.ex.experimental.Version
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests the move reports of [ReplayWalker]. The walk reports a move only when it takes the whole
 * run, and the moved text is intact in the effect version.
 */
internal class ReplayWalkerMoveTest {

  @Test
  fun `an intact move reports both halves as a move`() {
    val a = BASE.fork(A).move(0, 2, 4)
    val b = BASE.fork(B).move(6, 8, 10)
    val moveOfA = listOf("ins(4, 01, from 0)", "del(0, 2, copy at 4)")
    val moveOfB = listOf("ins(10, 67, from 6)", "del(6, 2, copy at 10)")
    val merged = a.merge(b)
    assertEquals(moveOfA + moveOfB, reportsOfMerge(BASE, merged))
    // The walk over the move of the receiver itself is silent.
    assertEquals(moveOfB, reportsOfMerge(a, b))
  }

  @Test
  fun `a concurrent insert inside the source makes both halves plain`() {
    // The copy of A lands between "6" and "7", inside the source of B.
    val a = BASE.fork(A).move(0, 2, 7)
    val b = BASE.fork(B).move(6, 8, 10)
    val merged = a.merge(b)
    val expected = listOf(
      "ins(7, 01, from 0)",
      "del(0, 2, copy at 7)",
      "ins(10, 67)",
      "del(4, 1)",
      "del(6, 1)",
    )
    assertEquals(expected, reportsOfMerge(BASE, merged))
  }

  @Test
  fun `a concurrent delete in the source makes both halves plain`() {
    val a = BASE.fork(A).move(0, 2, 4)
    val firstDeleted = BASE.fork(B).applyOp(DocumentOp.deleteOp(0, 1))
    val firstMerged = firstDeleted.merge(a)
    assertEquals(listOf("del(0, 1)", "ins(3, 01)", "del(0, 1)"), reportsOfMerge(BASE, firstMerged))
    val secondDeleted = BASE.fork(B).applyOp(DocumentOp.deleteOp(1, 1))
    val secondMerged = secondDeleted.merge(a)
    assertEquals(listOf("del(1, 1)", "ins(3, 01)", "del(0, 1)"), reportsOfMerge(BASE, secondMerged))
  }

  @Test
  fun `an edit typed and deleted inside the source keeps the move`() {
    // The source holds an item that both versions deleted, so its delete arrives in two pieces.
    val move = listOf("ins(4, 01, from 0)", "del(0, 1, copy at 4)", "del(0, 1, copy at 4)")
    val own = BASE.fork(A)
      .applyOp(DocumentOp.insertOp(1, "x"))
      .applyOp(DocumentOp.deleteOp(1, 1))
      .move(0, 2, 4)
    assertEquals(listOf("ins(1, x)", "del(1, 1)") + move, reportsOfMerge(BASE, own))
    // The source holds an item that the prepare version has not applied, and the effect version deleted.
    val other = BASE.fork(B)
      .applyOp(DocumentOp.insertOp(1, "y"))
      .applyOp(DocumentOp.deleteOp(1, 1))
    val a = BASE.fork(A).move(0, 2, 4)
    val merged = other.merge(a)
    assertEquals(listOf("ins(1, y)", "del(1, 1)") + move, reportsOfMerge(BASE, merged))
  }

  @Test
  fun `a part of a move run reports a plain insert`() {
    val moved = DocBranch.createBranch("0123", A).move(0, 2, 4)
    val graph = EventGraphImpl.implOf(moved.graph())
    // The version ends at the first unit of the move insert.
    val firstUnitOfMove = LvVersion(intArrayOf(0))
    assertEquals(listOf("ins(4, 0)"), reportsOfReplay(graph, firstUnitOfMove))
    val whole = listOf("ins(4, 01, from 0)", "del(0, 2, copy at 4)")
    assertEquals(whole, reportsOfReplay(graph, graph.lvVersion()))
  }

  @Test
  fun `a move offset outside the document reports plain halves`() {
    // An event checks only the arithmetic of its move, so the walk checks the bounds.
    val u = Agent.createAgent("u")
    val text = Event.createInsert(u, 0, 0, "ab")
    val insertFromOutside = Event.create(u, 2, DocumentOp.insertOp(2, "x", 5))
    val deleteToOutside = Event.create(u, 3, DocumentOp.deleteOp(0, 1, 5))
    var graph = EventGraph.createGraph().append(text, Version.root())
    graph = graph.append(insertFromOutside, graph.version())
    graph = graph.append(deleteToOutside, graph.version())
    val impl = EventGraphImpl.implOf(graph)
    val reports = reportsOfReplay(impl, impl.lvVersion())
    assertEquals(listOf("ins(0, ab)", "ins(2, x)", "del(0, 1)"), reports)
  }

  @Test
  fun `a move source in the unused placeholder tail arrives as a plain insert`() {
    // The base deleted "cd", so the placeholder has 6 units for a text of 2. The walk cannot tell the
    // unused tail from the text, and the sink must not trust the source position it reports.
    val u = Agent.createAgent("u")
    val v = Agent.createAgent("v")
    val text = Event.createInsert(u, 0, 0, "abcd")
    val deletion = Event.createDelete(u, 4, 2, 2)
    var base = EventGraph.createGraph().append(text, Version.root())
    base = base.append(deletion, base.version())
    val faultyMove = Event.create(v, 0, DocumentOp.insertOp(2, "x", 4))
    val other = base.append(faultyMove, base.version())
    val merged = EventGraphImpl.implOf(base.mergeFrom(other))
    val baseVersion = EventGraphImpl.implOf(base).lvVersion()
    val reports = ReportSink()
    EgWalkerReplay.mergeInto(merged, baseVersion, reports)
    assertEquals(listOf("ins(2, x, from 4)"), reports.reports())
    val sink = BatchingSink(DocumentText.createText("ab"))
    EgWalkerReplay.mergeInto(merged, baseVersion, sink)
    assertEquals(listOf(DocumentOp.insertOp(2, "x")), sink.ops())
  }

  /**
   * The reports of a merge of [other] into [receiver].
   */
  private fun reportsOfMerge(receiver: DocBranch, other: DocBranch): List<String> {
    val merged = EventGraphImpl.implOf(receiver.merge(other).graph())
    val receiverVersion = EventGraphImpl.implOf(receiver.graph()).lvVersion()
    val sink = ReportSink()
    EgWalkerReplay.mergeInto(merged, receiverVersion, sink)
    return sink.reports()
  }

  /**
   * The reports of a full replay of [graph] at [version]. The base text of [graph] reports nothing.
   */
  private fun reportsOfReplay(graph: EventGraphImpl, version: LvVersion): List<String> {
    val sink = ReportSink()
    val walker = ReplayWalker(graph, placeholderCount = graph.baseText().length())
    walker.replayAt(version, sink)
    return sink.reports()
  }

  /**
   * A text move as `DocumentEx.moveText` makes it: an insert of the copy, then a delete of the source.
   */
  private fun DocBranch.move(srcStart: Int, srcEnd: Int, dst: Int): DocBranch {
    val fragment = text().chars().subSequence(srcStart, srcEnd)
    var sourceStart = srcStart
    if (dst < srcStart) {
      // The copy lands before the source and shifts it.
      sourceStart += fragment.length
    }
    val copied = applyOp(DocumentOp.insertOp(dst, fragment, sourceStart))
    return copied.applyOp(DocumentOp.deleteOp(sourceStart, fragment.length, dst))
  }

  private class ReportSink : EgWalkerReplay.Sink {
    private val reports = ArrayList<String>()

    fun reports(): List<String> {
      return reports
    }

    override fun insert(effectPos: Int, fragment: CharSequence) {
      reports.add("ins($effectPos, $fragment)")
    }

    override fun delete(effectPos: Int, count: Int) {
      reports.add("del($effectPos, $count)")
    }

    override fun moveInsert(effectPos: Int, fragment: CharSequence, sourceEffectPos: Int) {
      reports.add("ins($effectPos, $fragment, from $sourceEffectPos)")
    }

    override fun moveDelete(effectPos: Int, count: Int, copyEffectPos: Int) {
      reports.add("del($effectPos, $count, copy at $copyEffectPos)")
    }
  }

  private companion object {
    val A: Agent = Agent.createAgent("a")
    val B: Agent = Agent.createAgent("b")
    val BASE: DocBranch = DocBranch.createBranch("0123456789", Agent.createAgent("base"))
  }
}
