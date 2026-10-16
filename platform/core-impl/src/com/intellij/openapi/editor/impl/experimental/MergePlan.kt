// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.experimental.Event

/**
 * What a merge of [other] into [dest] will append, worked out before anything is appended.
 *
 * The plan is what makes a merge ATOMIC. Building it reads both graphs and can reject, but
 * it changes nothing, so a rejected merge leaves no run behind. Applying it cannot reject.
 *
 * The plan holds one entry per run of [other] that carries a unit [dest] lacks, in ASCENDING lv
 * order. That order is required. A parent always sits at a smaller lv, so an append in that order
 * puts every parent in place before its child names it.
 *
 * [destStarts] is the part that needs explaining. A run of [other] may name a parent that the
 * plan itself is about to bring in, and [dest] does not hold it yet. So the plan reserves the lv
 * of every entry up front and answers such a parent from the reservation, arithmetically. That
 * also spares an index lookup for each parent inside the new region.
 */
internal class MergePlan(
  private val dest: EventGraphImpl,
  private val other: EventGraphImpl,
  private val summary: VersionSummary,
) {

  /**
   * The [StoredRun.lvStart] in [other] of every run with something new, ascending.
   */
  private val newStarts: IntArray = other.newRunStarts(summary)

  /**
   * The three below run in step with [newStarts], so one index names one planned entry.
   */
  private val events = ArrayList<EventImpl>(newStarts.size)
  private val parents = ArrayList<LvVersion>(newStarts.size)
  private val destStarts = IntArray(newStarts.size) { NO_UNIT }

  /**
   * The first lv after everything the plan appends.
   */
  private var lvEnd: LV = dest.size()

  private val remappedVersion: LvVersion

  init {
    for (index in newStarts.indices) {
      planEntry(index)
    }
    // The version remap rejects an unknown unit too, so it belongs in the plan.
    val remapped = IntArray(other.lvVersion().lvs.size) { i: Int ->
      remap(other.lvVersion().lvs[i])
    }
    remapped.sort()
    remappedVersion = LvVersion(remapped)
  }

  /**
   * The number of runs the plan appends.
   */
  fun size(): Int {
    return events.size
  }

  /**
   * The run to append at [index], already cut down to the part [dest] lacks.
   */
  fun eventAt(index: Int): EventImpl {
    return events[index]
  }

  /**
   * The parents that the run at [index] gets, in the merged graph's lvs.
   */
  fun parentsAt(index: Int): LvVersion {
    return parents[index]
  }

  /**
   * The version of [other], re-expressed in the merged graph's lvs.
   */
  fun remappedOtherVersion(): LvVersion {
    return remappedVersion
  }

  private fun planEntry(index: Int) {
    val run = other.runAt(newStarts[index])
    val event = run.event
    val known = knownUnits(event)
    val entryParents = if (known == 0) {
      val runParents = run.runParents()
      val remapped = IntArray(runParents.size) { i: Int ->
        remap(runParents[i])
      }
      remapped.sort()
      remapped
    } else {
      // The destination holds the leading units, so the last of them is the only parent.
      val lv = dest.lvOfUnit(event.agent(), event.seq() + known - 1)
      checkRemapped(lv, newStarts[index] + known - 1)
      intArrayOf(lv)
    }
    val suffix = event.suffixFrom(known)
    checkUnitSpace(suffix.length())
    events.add(suffix)
    parents.add(LvVersion(entryParents))
    destStarts[index] = lvEnd
    lvEnd += suffix.length()
  }

  /**
   * The leading units of [event] that [dest] already holds.
   */
  private fun knownUnits(event: Event): Int {
    return (summary.endSeq(event.agent()) - event.seq()).coerceIn(0, event.length())
  }

  /**
   * The lv that the unit [otherLv] of [other] has in the merged graph. It is the lv that [dest]
   * already gave it, or the one that this plan reserves.
   */
  private fun remap(otherLv: LV): LV {
    val run = other.runAt(otherLv)
    val event = run.event
    val seq = run.seqAt(otherLv)
    if (seq < summary.endSeq(event.agent())) {
      val lv = dest.lvOfUnit(event.agent(), seq)
      checkRemapped(lv, otherLv)
      return lv
    }
    // A planned run keeps the place of its lvStart in newStarts. Its reservation is filled in by
    // now, because a parent precedes its child and so was planned earlier.
    val entry = newStarts.binarySearch(run.lvStart)
    val destStart = if (entry >= 0) destStarts[entry] else NO_UNIT
    checkRemapped(destStart, otherLv)
    return destStart + (run.unitIndexOf(otherLv) - knownUnits(event))
  }

  private fun checkRemapped(lv: LV, otherLv: LV) {
    require(lv >= 0) {
      "The unit at the other graph's lv $otherLv is not in the merged graph"
    }
  }

  /**
   * Fails when the planned runs would take the unit space past [EventGraphImpl.unitCapacity].
   */
  private fun checkUnitSpace(length: Int) {
    require(length <= dest.unitCapacity() - lvEnd) {
      "The merged unit space overflows: the plan reaches $lvEnd of ${dest.unitCapacity()} and adds $length"
    }
  }

  /**
   * The size of the plan and not its content: the content is a whole region of a history.
   */
  override fun toString(): String {
    return "MergePlan(runs=${events.size}, units=${lvEnd - dest.size()}, " +
           "from a graph of ${other.size()} units in ${other.runCount()} runs)"
  }
}
