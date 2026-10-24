// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.applyOp
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.ex.experimental.Event
import com.intellij.openapi.editor.ex.experimental.EventGraph
import com.intellij.openapi.editor.ex.experimental.Version
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * A fuzz that checks every graph invariant after every step, through [EventGraphImpl.checkInvariants].
 * The fuzz tests of the platform tests compare texts only, and they cannot reach that check.
 *
 * It has two shapes. In the first, replicas of one [DocumentBranch] edit and pull in a random order,
 * and the ops of each pull must fold the old text into the new one. In the second, raw graphs
 * append at random past versions. So a run can start inside another run, name several parents,
 * or be concurrent with an earlier run of its own agent.
 */
internal class EventGraphInvariantFuzzTest {

  @Test
  fun `branches that edit and pull keep every invariant`() {
    repeat(ROUNDS) { round ->
      fuzzRound(20260930L, round) { random ->
        val base = DocumentBranch.createBranch(randomText(random, 8), Agent.createAgent("base"))
        val replicas = (0 until 2 + random.nextInt(3)).mapTo(ArrayList()) { base.fork(Agent.createAgent("agent$it")) }
        val carets = IntArray(replicas.size)
        repeat(STEPS) { step ->
          val i = random.nextInt(replicas.size)
          if (random.nextInt(3) == 0) {
            val merge = replicas[i].mergeWithOps(replicas[random.nextInt(replicas.size)])
            val folded = merge.ops().fold(replicas[i].text()) { text, op -> text.applyOp(op) }
            assertEquals(merge.branch().text().string(), folded.string()) {
              "round $round, step $step, the ops of replica $i"
            }
            replicas[i] = merge.branch()
          } else {
            val op = randomOp(random, replicas[i].text().length(), carets[i])
            replicas[i] = replicas[i].applyOp(op)
            carets[i] = if (op is DocumentOp.Insert) op.offset() + op.length() else op.offset()
          }
          checkGraph(replicas[i].graph()) { "round $round, step $step, replica $i" }
          assertEquals(replicas[i].graph().replay().string(), replicas[i].text().string()) {
            "round $round, step $step, replica $i"
          }
        }
      }
    }
  }

  @Test
  fun `raw appends at past versions keep every invariant`() {
    repeat(ROUNDS) { round ->
      fuzzRound(20261001L, round) { random ->
        val agents = (0 until 2 + random.nextInt(2)).map { Agent.createAgent("agent$it") }
        // Every replica grows from one graph with a base text, so any two of them merge, and each
        // replay starts from that text.
        val origin = DocumentBranch.createBranch(randomText(random, 8), Agent.createAgent("base")).graph()
        val graphs = agents.mapTo(ArrayList()) { origin }
        // Every version each replica passed through. A replica only appends, so each one stays valid.
        val versions = agents.mapTo(ArrayList()) { arrayListOf(Version.root()) }
        val carets = IntArray(agents.size)
        repeat(STEPS) { step ->
          val i = random.nextInt(graphs.size)
          val graph = graphs[i]
          if (random.nextInt(4) == 0) {
            graphs[i] = graph.mergeFrom(graphs[random.nextInt(graphs.size)])
          } else {
            val atTip = random.nextBoolean()
            val parents = if (atTip) graph.version() else versions[i][random.nextInt(versions[i].size)]
            val text = graph.replay(parents).string()
            val caret = if (atTip) carets[i] else random.nextInt(text.length + 1)
            val seq = EventGraphImpl.implOf(graph).nextSeqFor(agents[i])
            val event = randomEvent(random, agents[i], seq, text, caret)
            graphs[i] = graph.append(event, parents)
            carets[i] = event.op().offset() + if (event.op() is DocumentOp.Insert) event.length() else 0
          }
          versions[i].add(graphs[i].version())
          checkGraph(graphs[i]) { "round $round, step $step, replica $i" }
        }
        // A full sync in two orders gives one text.
        val forward = graphs.fold(origin) { all, graph -> all.mergeFrom(graph) }
        val backward = graphs.foldRight(origin) { graph, all -> all.mergeFrom(graph) }
        checkGraph(forward) { "round $round, the forward sync" }
        checkGraph(backward) { "round $round, the backward sync" }
        assertEquals(forward.replay().string(), backward.replay().string()) { "round $round" }
      }
    }
  }

  /**
   * The check must be able to fail. [EventGraph.append] leaves the reduction of parents to its caller.
   */
  @Test
  fun `the invariant check rejects parents that are not reduced`() {
    val graph = EventGraph.createGraph()
      .append(Event.createInsert(Agent.createAgent("u"), 0, 0, "ab"), Version.root())
      .append(Event.createInsert(Agent.createAgent("v"), 0, 1, "x"), Version.of(0, 1))
    val failure = assertThrows(IllegalArgumentException::class.java) { EventGraphImpl.implOf(graph).checkInvariants() }
    assertTrue(failure.message.orEmpty().contains("not reduced"), failure.message)
  }

  /**
   * Runs one round with a [Random] of its own seed, so a failing round replays alone.
   */
  private fun fuzzRound(seed: Long, round: Int, body: (Random) -> Unit) {
    try {
      body(Random(seed + round))
    } catch (e: Throwable) {
      throw AssertionError("The fuzz round $round with the seed ${seed + round} failed: ${e.message}", e)
    }
  }

  /**
   * Fails unless every invariant of [graph] holds. The diagram of a random graph must draw, too.
   */
  private fun checkGraph(graph: EventGraph, where: () -> String) {
    try {
      EventGraphImpl.implOf(graph).checkInvariants()
      graph.toString()
    } catch (e: IllegalArgumentException) {
      throw AssertionError("${where()}: ${e.message}", e)
    }
  }

  private fun randomOp(random: Random, length: Int, caret: Int): DocumentOp.Text {
    val at = if (random.nextInt(4) == 0) random.nextInt(length + 1) else caret.coerceIn(0, length)
    if (length > 0 && random.nextInt(3) == 0) {
      val offset = at.coerceAtMost(length - 1)
      return DocumentOp.deleteOp(offset, 1 + random.nextInt(minOf(3, length - offset)))
    }
    return DocumentOp.insertOp(at, randomText(random, 1 + random.nextInt(3)))
  }

  /**
   * An event that fits [text], the document at its parents. It edits at [caret] when it can.
   */
  private fun randomEvent(
    random: Random,
    agent: Agent,
    seq: Long,
    text: String,
    caret: Int,
  ): Event {
    val at = caret.coerceIn(0, text.length)
    if (text.isNotEmpty() && random.nextInt(3) == 0) {
      val offset = at.coerceAtMost(text.length - 1)
      return Event.createDelete(agent, seq, offset, 1 + random.nextInt(minOf(3, text.length - offset)))
    }
    return Event.createInsert(agent, seq, at, randomText(random, 1 + random.nextInt(3)))
  }

  private fun randomText(random: Random, length: Int): String {
    return String(CharArray(length) { ALPHABET[random.nextInt(ALPHABET.length)] })
  }

  private companion object {
    const val ROUNDS = 300
    const val STEPS = 100
    const val ALPHABET = "abcxyz \n"
  }
}
