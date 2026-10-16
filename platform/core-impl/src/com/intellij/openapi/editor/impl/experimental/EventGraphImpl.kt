// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.Event
import com.intellij.openapi.editor.ex.experimental.EventGraph
import com.intellij.openapi.editor.ex.experimental.TrimmedHistoryException
import com.intellij.openapi.editor.ex.experimental.Version
import org.jetbrains.annotations.TestOnly
import java.util.BitSet

/**
 * See [EventGraph]. An immutable value: the closed runs in a persistent [RunTree], the same runs
 * by agent in an [AgentIndex], then the newest run, the [tail]. Together they cover the lvs
 * `[0, size)`. The [base] holds the base text, the base version and the lineage. Every value that
 * grows from a graph by an append or a merge keeps its base.
 *
 * A trim keeps only the units above the base version, and the empty lv version names the base
 * version. So the walks stop at the base as they stop at the root, and a replay starts from the
 * base text. The [AgentIndex] knows where the seqs of each agent start.
 *
 * Every append returns a new value, and the new trees share every node they do not change with
 * the old ones. So two siblings of one value append each on their own, and neither copies the
 * history. No value holds anything that another value can change.
 *
 * The tail stays out of the trees, because an append that continues it replaces it with a longer
 * run (see [StoredRun.tryAppend]). The next append that does not continue the tail closes it
 * into the trees.
 *
 * Thread safety: nothing here is mutable. Every field is final, every structure it reaches is
 * immutable, and nothing writes a parents array after construction. So a thread that obtains a
 * value, even through a data race, sees the whole value as it was built. The holder of "the
 * current value" is the only place that needs a publication, and it is outside this class.
 *
 * Only a graph with no events has no tail. [checkTail] enforces that when a graph is built, and
 * [requireTail] relies on it.
 */
internal class EventGraphImpl private constructor(
  private val base: GraphBase,
  private val runs: RunTree,
  private val agents: AgentIndex,
  private val tail: StoredRun?,
  private val size: Int,
  private val version: LvVersion,
) : EventGraph {

  init {
    checkTail()
    checkVersionEnd()
  }

  override fun size(): Int {
    return size
  }

  override fun runCount(): Int {
    return if (tail == null) runs.size() else runs.size() + 1
  }

  override fun version(): Version {
    return VersionImpl.fromLvVersion(this, version)
  }

  override fun append(event: Event, parents: Version): EventGraph {
    return appendImpl(EventImpl.implOf(event), lvVersionOf(parents))
  }

  override fun mergeFrom(other: EventGraph): EventGraph {
    return mergeFromImpl(implOf(other)).graph()
  }

  override fun replay(version: Version): DocumentText {
    return replayAt(lvVersionOf(version))
  }

  /**
   * The replay at this graph's own frontier, which needs no conversion.
   */
  override fun replay(): DocumentText {
    return replayAt(version)
  }

  fun lvVersion(): LvVersion {
    return version
  }

  /**
   * The base text: the text before the first run. The replay of [LvVersion.ROOT] returns it.
   */
  fun baseText(): DocumentText {
    return base.text()
  }

  /**
   * The version where the history of this graph starts. The graph stores none of its heads.
   */
  fun baseVersion(): VersionImpl {
    return base.version()
  }

  /**
   * The first seq of [agent] that this graph stores: 0, or the next seq of [agent] at the base version.
   */
  fun startSeqOf(agent: Agent): Int {
    return agents.startSeq(agent)
  }

  /**
   * Whether a trim dropped the unit ([agent], [seq]). The seqs of an agent below its start are gone.
   */
  fun hasTrimmed(agent: Agent, seq: Int): Boolean {
    return seq < startSeqOf(agent)
  }

  /**
   * The parents of the unit [lv] by event id. A unit with no parent follows the base version.
   */
  fun parentVersionOf(lv: LV): VersionImpl {
    return VersionImpl.fromLvVersion(this, LvVersion(parentsOf(lv)))
  }

  /**
   * This graph with the base moved to [safepoint]. [text] must be the document at [safepoint]. The
   * units at or below [safepoint] are gone. The graph keeps its lineage and its version.
   *
   * [safepoint] must be [LvVersion.ROOT], which changes nothing, or the version of this graph. A
   * trim to the version keeps no unit, and it costs one entry per agent and per head.
   */
  fun trimmedTo(safepoint: LvVersion, text: DocumentText): EventGraphImpl {
    if (safepoint == LvVersion.ROOT) {
      return this
    }
    checkTrimToVersion(safepoint)
    val baseVersion = VersionImpl.fromLvVersion(this, version)
    return EventGraphImpl(
      base = base.movedTo(text, baseVersion),
      runs = RunTree.EMPTY,
      agents = AgentIndex.startingAt(summarize()),
      tail = null,
      size = 0,
      version = LvVersion.ROOT,
    )
  }

  /**
   * The most units that this graph can hold. The replay counts the base text in the space of the
   * units, so the base text takes its share. Then no merge overflows the space of a replay.
   */
  fun unitCapacity(): Int {
    return Int.MAX_VALUE - base.text().length()
  }

  /**
   * The heads of [version] as lvs of this graph. Fails when this graph does not hold one of them.
   * An [LvNamedVersion] names lvs already, so only the size of this graph can reject it.
   */
  fun lvVersionOf(version: Version): LvVersion {
    if (version is LvNamedVersion) {
      val lvVersion = version.lvVersion()
      checkVersionFits(lvVersion)
      return lvVersion
    }
    return VersionImpl.implOf(version).lvVersionIn(this)
  }

  private fun replayAt(lvVersion: LvVersion): DocumentText {
    val text = StringBuilder(base.text().chars())
    EgWalkerReplay.replay(this, lvVersion, StringBuilderSink(text))
    return DocumentText.createText(text)
  }

  /**
   * Appends the [event] run at this graph's own frontier.
   */
  fun appendAtVersion(event: EventImpl): EventGraphImpl {
    return appendImpl(event, version)
  }

  // --------------------------------------------------- what this graph knows, by event id
  //
  // Each of these combines the tail with the agent index. Both belong to this value alone, so a
  // graph answers only for itself.

  /**
   * The next free seq of [agent] in this graph. The tail is the newest run, and the seqs of
   * one agent ascend with the lvs, so a tail of [agent] holds its greatest seq.
   */
  fun nextSeqFor(agent: Agent): Int {
    val tail = tail
    if (tail != null && tail.event.agent() == agent) {
      return tail.endSeq()
    }
    return agents.nextSeq(agent)
  }

  /**
   * What this graph knows, as one end seq per agent.
   */
  fun summarize(): VersionSummary {
    return agents.summarize(tail)
  }

  /**
   * The lv of the unit ([agent], [seq]) in this graph, or [NO_UNIT] when it holds no such unit.
   */
  fun lvOfUnit(agent: Agent, seq: Int): LV {
    val tail = tail
    if (tail != null && tail.holdsUnit(agent, seq)) {
      return tail.lvOfSeq(seq)
    }
    return agents.lvOfSeq(agent, seq)
  }

  /**
   * The [StoredRun.lvStart] of every run of THIS graph that [summary] does not cover, ascending.
   * [summary] must cover the units that a trim dropped, because no run holds them.
   */
  fun newRunStarts(summary: VersionSummary): IntArray {
    checkCoversStartSeqs(summary)
    val starts = agents.newRunStarts(summary)
    val tail = tail
    if (tail == null || tail.endSeq() <= summary.endSeq(tail.event.agent())) {
      return starts
    }
    // The tail is the newest run, so its start goes last and the result still ascends.
    return starts + tail.lvStart
  }

  /**
   * The union of the two graphs, joined by event ids, and whether the merge is a fast-forward. A
   * run of [other] that this graph knows in part contributes only its unknown suffix. That suffix
   * goes through the same append as a local edit, so it extends the tail when it continues it.
   *
   * The cost is the size of the CHANGE, not the size of either history. A [VersionSummary] holds
   * one integer per agent, so the two graphs compare their histories without a walk of the history
   * they share. The id check reads a few shared units per agent. The plan reads one run for each
   * parent of a new run, and loads only the runs that go past this graph.
   *
   * The merge is ATOMIC. It plans every new run first, and only then appends. So a rejected merge
   * fails before the first append, and by then no append can fail. The values are immutable, so
   * even a failed append would leave this graph as it was.
   *
   * The result keeps the base of this graph. [other] must have the same lineage. The base version of
   * each graph must be a critical version of the other one. So the other graph holds it, and every
   * unit that only the other graph holds descends from it. Otherwise the merge throws
   * [TrimmedHistoryException].
   *
   * Each graph checks its own base, so the merge also runs the checks of the reverse merge. Then
   * `a.mergeFromImpl(b)` throws exactly when `b.mergeFromImpl(a)` throws. The merge builds the
   * reverse plan only when that plan can reject. See [canRejectReverseMerge].
   */
  fun mergeFromImpl(other: EventGraphImpl): MergeResult {
    base.assertSameLineage(other.base)
    // The delta: what this graph knows, then only the runs of the other graph that go past
    // it. Neither step walks the history the two graphs share, so a merge costs the CHANGE and
    // not the session. The reference does this with summarizeVersion and intersectWithSummary.
    val summary = summarize()
    val otherSummary = other.summarize()
    checkHoldsTrimmedUnitsOf(other, summary)
    other.checkHoldsTrimmedUnitsOf(this, otherSummary)
    SharedIdCheck(this, other).check(summary, otherSummary)
    val plan = MergePlan(this, other, summary)
    if (canRejectReverseMerge(other)) {
      checkReverseMerge(other, otherSummary)
    }
    if (plan.size() == 0) {
      return MergeResult(this, size, isFastForward = false)
    }
    // The remap of the other version can reject too, so it runs before the first append.
    val remappedOtherVersion = plan.remappedOtherVersion()
    // Apply. Every check inside appendImpl already held against the plan, so each one now
    // re-asserts the plan and none of them can reject.
    var graph = this
    for (index in 0 until plan.size()) {
      graph = graph.appendImpl(plan.eventAt(index), plan.parentsAt(index))
    }
    val isFastForward = graph.version == remappedOtherVersion
    return MergeResult(graph, size, isFastForward)
  }

  /**
   * Extends the tail when [event] continues it, and otherwise closes the tail into the trees
   * and makes [event] the new tail. The units and their parents are the same either way, so
   * only [runCount] can tell the two apart.
   */
  private fun appendImpl(event: EventImpl, parents: LvVersion): EventGraphImpl {
    checkVersionFits(parents)
    checkLvSpace(event.length())
    checkNextSeq(event)
    val newSize = size + event.length()
    val newVersion = version.advancedBy(parents, newSize - 1)
    val tail = tail
    val extended = tail?.tryAppend(event, parents)
    if (extended != null) {
      return EventGraphImpl(
        base = base,
        runs = runs,
        agents = agents,
        tail = extended,
        size = newSize,
        version = newVersion,
      )
    }
    val newTail = StoredRun(event, size, parents.lvs)
    if (tail == null) {
      return EventGraphImpl(
        base = base,
        runs = runs,
        agents = agents,
        tail = newTail,
        size = newSize,
        version = newVersion,
      )
    }
    return EventGraphImpl(
      base = base,
      runs = runs.appended(tail.lvStart, tail),
      agents = agents.appended(tail),
      tail = newTail,
      size = newSize,
      version = newVersion,
    )
  }

  // ------------------------------------------------------------------- queries for the replay

  /**
   * The run that covers [lv].
   */
  fun runAt(lv: LV): StoredRun {
    checkLv(lv)
    val tail = requireTail()
    return if (tail.startsAtOrBefore(lv)) tail else requireClosedRun(lv)
  }

  /**
   * The index of the run that covers [lv]. An index counts runs, and an lv counts units.
   */
  fun runIndexOf(lv: LV): Int {
    checkLv(lv)
    val tail = requireTail()
    return if (tail.startsAtOrBefore(lv)) runs.size() else runs.floorIndex(lv)
  }

  /**
   * The run at [index], which must be below [runCount]. The tail comes last.
   */
  fun runByIndex(index: Int): StoredRun {
    checkRunIndex(index)
    return if (index == runs.size()) requireTail() else runs.get(index)
  }

  /**
   * The closed run that covers [lv], for an lv below the tail. The closed runs start at lv 0 and
   * leave no gap, so [RunTree.floor] always finds one there.
   */
  private fun requireClosedRun(lv: LV): StoredRun {
    val run = runs.floor(lv)
    require(run != null) {
      "No closed run covers the lv $lv, below the tail of a graph of size $size"
    }
    return run
  }

  /**
   * The tail, for a caller that has checked an lv or a run index against this graph. That
   * check passes only in a graph that holds at least one unit, and [checkTail] makes sure that
   * such a graph has a tail. So this fails only when [checkTail] itself is wrong.
   */
  private fun requireTail(): StoredRun {
    val tail = tail
    require(tail != null) {
      "A graph of size $size has no tail, but only a graph with no events may have none"
    }
    return tail
  }

  fun parentsOf(lv: LV): Frontier {
    return runAt(lv).parentsOf(lv)
  }

  fun isDeleteAt(lv: LV): Boolean {
    return runAt(lv).isDelete()
  }

  /**
   * The tie-break order for concurrent insertions of the units [lvA] and [lvB]: by agent, then by
   * seq. [runA] is the run that holds [lvA]. A scan compares one unit with many others, so it looks
   * that run up once. The name is the one of the reference implementation.
   */
  fun lvCmp(runA: StoredRun, lvA: LV, lvB: LV): Int {
    checkRunHolds(runA, lvA)
    val runB = runAt(lvB)
    val byAgent = runA.event.agent().compareTo(runB.event.agent())
    if (byAgent != 0) {
      return byAgent
    }
    return runA.seqAt(lvA).compareTo(runB.seqAt(lvB))
  }

  // ------------------------------------------------------------------------------------ checks

  /**
   * Fails when any value invariant of the graph does not hold. The constructor checks only the
   * ones that cost O(1), and this checks all of them, in O(size) and more. A test calls it after
   * each step of a random history. The invariants:
   * - the runs cover the lvs `[0, size)` in order, with no gap, and the tail is the last run;
   * - each run is keyed by its [StoredRun.lvStart], and the agent index holds exactly the closed runs;
   * - the seqs of each agent start at its start seq, ascend with the lvs, and leave no gap;
   * - every parent of a run sits below the run, and the parents of a run are transitively reduced;
   * - the version is exactly the set of units that have no child.
   */
  @TestOnly
  fun checkInvariants() {
    val runList = ArrayList<StoredRun>(runCount())
    for (index in 0 until runs.size()) {
      runList.add(runs.get(index))
    }
    val tail = tail
    if (tail != null) {
      runList.add(tail)
    }
    val closedStarts = agents.newRunStarts(agents.startSeqs())
    require(closedStarts.contentEquals(IntArray(runs.size()) { runs.get(it).lvStart })) {
      "The agent index does not hold exactly the closed runs"
    }
    val nextSeqs = HashMap<Agent, Int>()
    val childless = BitSet(size)
    var lvEnd = 0
    for ((index, run) in runList.withIndex()) {
      require(run.lvStart == lvEnd) {
        "The run $run does not start where the run before it ends, at $lvEnd"
      }
      require(index == runs.size() || runs.floor(run.lvStart) === run) {
        "The closed run $run is not keyed by its first lv"
      }
      val agent = run.event.agent()
      val expectedSeq = nextSeqs[agent] ?: agents.startSeq(agent)
      require(run.event.seq() == expectedSeq) {
        "The run $run does not continue the seqs of $agent: expected $expectedSeq"
      }
      nextSeqs[agent] = run.endSeq()
      require(lvOfUnit(agent, run.event.seq()) == run.lvStart) {
        "The id index does not find the run $run"
      }
      checkRunParents(run)
      childless.set(run.lvEnd() - 1)
      for (parent in run.runParents()) {
        childless.clear(parent)
      }
      lvEnd = run.lvEnd()
    }
    require(lvEnd == size) {
      "The runs end at $lvEnd, but the graph size is $size"
    }
    for ((agent, nextSeq) in nextSeqs) {
      require(nextSeqFor(agent) == nextSeq) {
        "The next seq of $agent is ${nextSeqFor(agent)}, but the runs end at $nextSeq"
      }
    }
    val heads = IntArray(childless.cardinality())
    var next = 0
    var lv = childless.nextSetBit(0)
    while (lv >= 0) {
      heads[next] = lv
      next++
      lv = childless.nextSetBit(lv + 1)
    }
    require(version.lvs.contentEquals(heads)) {
      "The version $version is not the set of childless units v${heads.listedForMessage()}"
    }
  }

  /**
   * Fails when a parent of [run] is not below it, or when one parent is an ancestor of another.
   */
  private fun checkRunParents(run: StoredRun) {
    val parents = run.runParents()
    for (parent in parents) {
      require(parent in 0 until run.lvStart) {
        "The parent $parent of the run $run is not below it"
      }
    }
    if (parents.size < 2) {
      return
    }
    for (parent in parents) {
      val ancestors = eventsOf(LvVersion(intArrayOf(parent)))
      for (otherParent in parents) {
        require(otherParent == parent || !ancestors.get(otherParent)) {
          "The parents of the run $run are not reduced: $otherParent is an ancestor of $parent"
        }
      }
    }
  }

  /**
   * Fails when [summary] lacks a unit below the start seq of an agent, in a closed run or in the
   * tail. A merge rejects such a summary first, with [checkHoldsTrimmedUnitsOf].
   */
  private fun checkCoversStartSeqs(summary: VersionSummary) {
    val startSeqs = agents.startSeqs()
    for (agent in startSeqs.agents()) {
      val known = summary.endSeq(agent)
      val start = startSeqs.endSeq(agent)
      require(known >= start) {
        "The summary knows the seqs of $agent below $known, but this graph starts at $start"
      }
    }
  }

  /**
   * Throws [TrimmedHistoryException] when [other] trimmed units that [summary] lacks. The merge cannot
   * bring them in, because [other] no longer holds them.
   */
  private fun checkHoldsTrimmedUnitsOf(other: EventGraphImpl, summary: VersionSummary) {
    val otherStartSeqs = other.agents.startSeqs()
    for (agent in otherStartSeqs.agents()) {
      val otherStart = otherStartSeqs.endSeq(agent)
      val held = summary.endSeq(agent)
      if (held < otherStart) {
        val message = "The other graph trimmed the seqs of $agent below $otherStart, " +
                      "and this graph holds only the seqs below $held"
        throw TrimmedHistoryException(message)
      }
    }
  }

  /**
   * Whether the plan of the merge of this graph into [other] can reject. It cannot when the base
   * version of [other] is the root, or the base version of this graph. Every unit that this graph
   * adds then descends from it.
   */
  private fun canRejectReverseMerge(other: EventGraphImpl): Boolean {
    val otherBaseVersion = other.baseVersion()
    return !otherBaseVersion.isRoot() && otherBaseVersion != baseVersion()
  }

  /**
   * Throws [TrimmedHistoryException] when the merge of this graph into [other] would. The plan of
   * that merge maps every unit that this graph adds to [other]. So it builds only when those units
   * descend from the base version of [other]. The check appends nothing.
   */
  private fun checkReverseMerge(other: EventGraphImpl, otherSummary: VersionSummary) {
    MergePlan(other, this, otherSummary)
  }

  /**
   * Fails when [safepoint] is not the version of this graph. A trim to an older version needs the
   * base text there, which this graph does not build yet.
   */
  private fun checkTrimToVersion(safepoint: LvVersion) {
    require(safepoint == version) {
      "A trim to $safepoint below the version $version of the graph is not supported yet"
    }
  }

  /**
   * Fails when the tail does not fit the graph. Only a graph with no events has no tail. A tail is the
   * newest run, so it ends at [size], and the closed runs end where it starts. The queries for
   * the replay rely on all three.
   */
  private fun checkTail() {
    val tail = tail
    if (tail == null) {
      require(size == 0 && runs.size() == 0) {
        "A graph of size $size and ${runs.size()} closed runs has no tail, " +
        "but only a graph with no events may have none"
      }
      return
    }
    require(tail.lvEnd() == size) {
      "The tail $tail does not end at the graph size $size"
    }
    val closedEnd = runs.last()?.lvEnd() ?: 0
    require(closedEnd == tail.lvStart) {
      "The closed runs end at $closedEnd, but the tail $tail starts elsewhere"
    }
  }

  /**
   * Fails when the frontier does not end at the newest unit. Every append makes its last unit a
   * head, and no later lv exists, so the greatest head is always `size - 1`. A graph with no events
   * has the root version.
   */
  private fun checkVersionEnd() {
    require(version.unitSpan() == size) {
      "The version $version does not end at the newest unit of a graph of size $size"
    }
  }

  /**
   * Fails when [version] names an lv that this graph does not have. From outside, only a version
   * from [Version.of] reaches this check, and the size is all that its lvs can tell. So the lvs of
   * another graph pass when they are small enough, and they name other units here.
   */
  private fun checkVersionFits(version: LvVersion) {
    require(version.unitSpan() <= size) {
      "The version $version does not fit a graph of size $size"
    }
  }

  /**
   * Fails when [event] does not continue the seqs of its agent.
   *
   * The seqs of one agent must ascend and leave no gap. That rejects a reused id, which is the rule
   * one (agent, seq) pair names one unit forever. It also buys the delta merge. A graph then knows
   * exactly the seqs `[0, nextSeq)` of each agent. It stores them from the start seq of the agent,
   * and its base stands for the rest. So a [VersionSummary] is one integer per agent and not a set
   * of ranges.
   *
   * Each agent owns its own seq space, so two agents interleave freely.
   */
  private fun checkNextSeq(event: Event) {
    val expected = nextSeqFor(event.agent())
    require(event.seq() == expected) {
      "The event $event does not continue the seqs of its agent: expected $expected"
    }
  }

  /**
   * Fails when a run of [length] units cannot fit after the units of this graph, within
   * [unitCapacity].
   */
  private fun checkLvSpace(length: Int) {
    require(length <= unitCapacity() - size) {
      "The graph unit space overflows: capacity ${unitCapacity()}, size $size + run length $length"
    }
  }

  private fun checkRunHolds(run: StoredRun, lv: LV) {
    require(lv >= run.lvStart && lv < run.lvEnd()) {
      "The run $run does not hold the lv $lv"
    }
  }

  private fun checkLv(lv: LV) {
    require(lv in 0 until size) {
      "The lv $lv is out of the graph of size $size"
    }
  }

  private fun checkRunIndex(index: Int) {
    require(index in 0 until runCount()) {
      "The run index $index is out of the graph of ${runCount()} runs"
    }
  }

  /**
   * The graph as a text diagram. See [EventGraphDiagram] for the notation and its limits.
   */
  override fun toString(): String {
    return EventGraphDiagram.render(this)
  }

  private class StringBuilderSink(private val text: StringBuilder) : EgWalkerReplay.Sink {
    override fun insert(effectPos: Int, fragment: CharSequence) {
      text.insert(effectPos, fragment)
    }

    override fun delete(effectPos: Int, count: Int) {
      text.delete(effectPos, effectPos + count)
    }

    override fun toString(): String {
      return text.toString()
    }
  }

  companion object {
    /**
     * A graph with no events and an empty base text, in a new lineage.
     */
    fun empty(): EventGraphImpl {
      return startingFrom(DocumentText.createText(""))
    }

    /**
     * A graph with no events and the base text [text], in a new lineage.
     */
    fun startingFrom(text: DocumentText): EventGraphImpl {
      return EventGraphImpl(
        base = GraphBase.create(text),
        runs = RunTree.EMPTY,
        agents = AgentIndex.EMPTY,
        tail = null,
        size = 0,
        version = LvVersion.ROOT,
      )
    }

    fun implOf(graph: EventGraph): EventGraphImpl {
      require(graph is EventGraphImpl) {
        "Foreign EventGraph implementation: ${graph.javaClass.name}"
      }
      return graph
    }
  }
}
