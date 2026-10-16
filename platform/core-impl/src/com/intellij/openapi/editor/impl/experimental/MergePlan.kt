// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.experimental.Event
import com.intellij.openapi.editor.ex.experimental.TrimmedHistoryException

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
 *
 * Each graph names its base version with the empty parent set, so [remapParents] translates the
 * set as a whole. A run of [other] with no parent follows the base version of [other]. A set that
 * names exactly the base version of [dest] becomes the empty set. Any other set with a unit that
 * [dest] trimmed throws [TrimmedHistoryException], so the merge never guesses a text. So a plan
 * that builds proves that every new unit descends from the base version of [dest].
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

  init {
    for (index in newStarts.indices) {
      planEntry(index)
    }
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
   * The version of [other], re-expressed in the merged graph's lvs. It can reject like the plan, so
   * a merge asks for it before the first append.
   */
  fun remappedOtherVersion(): LvVersion {
    return remapParents(other.lvVersion().lvs)
  }

  private fun planEntry(index: Int) {
    val run = other.runAt(newStarts[index])
    val event = run.event
    val known = knownUnits(event)
    val entryParents = if (known == 0) {
      remapParents(run.runParents())
    } else {
      // The destination holds the leading units, so the last of them is the only parent.
      remapParents(intArrayOf(newStarts[index] + known - 1))
    }
    val suffix = event.suffixFrom(known)
    checkUnitSpace(suffix.length())
    events.add(suffix)
    parents.add(entryParents)
    destStarts[index] = lvEnd
    lvEnd += suffix.length()
  }

  /**
   * The units [otherParents] of [other] as a parent set of the merged graph. The empty set names the
   * base version of [other], which [dest] holds as units or as its own base version.
   */
  private fun remapParents(otherParents: Frontier): LvVersion {
    if (otherParents.isEmpty()) {
      return other.baseVersion().lvVersionIn(dest)
    }
    val remapped = IntArray(otherParents.size) { i: Int ->
      remap(otherParents[i])
    }
    if (remapped.contains(NO_UNIT)) {
      return trimmedParentsAsBase(otherParents)
    }
    remapped.sort()
    return LvVersion(remapped)
  }

  /**
   * The empty set for the units [otherParents] of [other], which name units that [dest] trimmed. Only
   * the base version of [dest] as a whole stands for trimmed units, so every other set throws
   * [TrimmedHistoryException].
   */
  private fun trimmedParentsAsBase(otherParents: Frontier): LvVersion {
    val parentIds = VersionImpl.fromLvVersion(other, LvVersion(otherParents))
    val destBaseVersion = dest.baseVersion()
    if (parentIds != destBaseVersion) {
      val message = "The other graph names the units $parentIds, which lie below the base version " +
                    "$destBaseVersion of this graph"
      throw TrimmedHistoryException(message)
    }
    return LvVersion.ROOT
  }

  /**
   * The leading units of [event] that [dest] already holds.
   */
  private fun knownUnits(event: Event): Int {
    return (summary.endSeq(event.agent()) - event.seq()).coerceIn(0, event.length())
  }

  /**
   * The lv that the unit [otherLv] of [other] has in the merged graph. It is the lv that [dest]
   * already gave it, or the one that this plan reserves. It is [NO_UNIT] when [dest] trimmed the unit.
   */
  private fun remap(otherLv: LV): LV {
    val run = other.runAt(otherLv)
    val event = run.event
    val seq = run.seqAt(otherLv)
    if (seq < summary.endSeq(event.agent())) {
      if (dest.hasTrimmed(event.agent(), seq)) {
        return NO_UNIT
      }
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
