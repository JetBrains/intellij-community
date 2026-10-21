// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

/**
 * The Eg-walker replay: rebuilds the document at a version from the event graph.
 * [ReplayWalker] holds the state and does the work; this is the entry point.
 *
 * This is a direct port of the reference implementation: `src/index.ts` of
 * https://github.com/josephg/eg-walker-reference at the commit `7287f4bc2c05`. A function that
 * the reference also has keeps the reference's name, so the two stay easy to compare. A helper
 * that only this port needs gets a descriptive name.
 *
 * The walk keeps the document at two versions at once. The *prepare* version is where the next
 * event was authored. The *effect* version has every walked event applied. The walk moves the
 * prepare version to each event's parents, applies the event, and reports the effect to a [Sink].
 *
 * Three documents therefore carry a position, and the names keep them apart. An `offset`
 * belongs to an op or an event, and it indexes the document at the PARENT version, which is
 * what the public `DocumentTextOp.offset` means. A `preparePos` indexes the prepare version, and an
 * `effectPos` indexes the effect version. A bare `pos` names no document, so the code does
 * not use one.
 *
 * The walk is run-length encoded on both sides. One item covers the units that one step applies,
 * which is usually a whole run. A step consumes as much of a run as the walk ranges hold. An item
 * splits only where an op needs a boundary inside it: a concurrent insert, a partial delete, or a
 * partial retreat or advance. Costs: the items sit in an [ItemTree], so a lookup costs O(log n) in
 * the items of the walked region, and a sequential run reuses a cached cursor. The search for a
 * right parent is a lookup too. The Fugue scan still visits the concurrent items before the place
 * of the new item, as in the reference. An item there that shares the left origin and the right
 * parent of the new item needs no index. The run-length encoding keeps the item count small.
 */
internal object EgWalkerReplay {

  /**
   * Where the walk reports its effects. Every report takes a whole span, because the walk is
   * run-length encoded on both sides and a span never crosses a run. A report always covers at
   * least one character, at a position of the effect version.
   *
   * A move arrives as one [moveInsert], then one [moveDelete] per piece of the source, with no other
   * report between them. A sink can still get one half alone, and it must take that half as a
   * plain report.
   */
  interface Sink {
    /**
     * Inserts [fragment] at [effectPos].
     */
    fun insert(effectPos: Int, fragment: CharSequence)

    /**
     * Removes [count] characters at [effectPos].
     */
    fun delete(effectPos: Int, count: Int)

    /**
     * Inserts [fragment] at [effectPos] as the first half of a move. [sourceEffectPos] is the
     * source in the effect version after the insert. A sink that tracks no moves takes a plain insert.
     */
    fun moveInsert(effectPos: Int, fragment: CharSequence, sourceEffectPos: Int) {
      insert(effectPos, fragment)
    }

    /**
     * Removes [count] characters at [effectPos] as the second half of a move, or as one piece of it.
     * [copyEffectPos] is the copy in the effect version before the delete. A sink that tracks no
     * moves takes a plain delete.
     */
    fun moveDelete(effectPos: Int, count: Int, copyEffectPos: Int) {
      delete(effectPos, count)
    }
  }

  /**
   * Rebuilds the text at [version] and reports every effect to [sink], in order. [sink] must start
   * with the base text of [graph], which one placeholder item stands for.
   */
  fun replay(graph: EventGraphImpl, version: LvVersion, sink: Sink) {
    val walker = ReplayWalker(graph, placeholderCount = graph.baseText().length())
    walker.replayAt(version, sink)
  }

  /**
   * Merges everything the graph holds beyond [branchVersion] into a document that is
   * already at [branchVersion], and reports only the new effects to [sink]. This is
   * the paper's partial replay: only the region above the common ancestor is walked.
   *
   * One placeholder item stands in for the whole document at the common ancestor, so the walk never
   * replays the units at or below it. The placeholder splits only where an op of the region lands.
   * A port of `mergeChangesIntoBranch` from the reference implementation, with the paper's
   * single-placeholder representation.
   *
   * Unlike the reference, every new unit must sit above every unit that only the branch holds.
   * The walk visits the units of the branch first, and [DeleteTargets] takes its pieces in
   * ascending lv order only. A merge that appended the other history to the graph of the branch
   * always gives that order, and [checkNewAboveConflict] checks it.
   */
  fun mergeInto(graph: EventGraphImpl, branchVersion: LvVersion, sink: Sink) {
    val conflict = graph.findConflicting(branchVersion.lvs, graph.lvVersion().lvs)
    checkNewAboveConflict(conflict)
    // One span of placeholder units, at least as long as the document at the common
    // ancestor. The trailing extras sit after every reachable position, inert.
    val baseLength = graph.baseText().length()
    val walker = ReplayWalker(graph, placeholderCount(baseLength, branchVersion))
    walker.startAt(conflict.commonAncestor)
    // The branch's own units above the ancestor rebuild the concurrency context. The
    // document already has them, so this phase reports nothing.
    walker.walk(conflict.conflictRanges, sink = null)
    // The units only in the merged history are the new ones, so they report.
    walker.walk(conflict.newRanges, sink)
  }

  /**
   * The length of the placeholder for the document at an ancestor of [branchVersion]: the base text
   * plus every unit that the version can name. That bounds the document, because an event adds at
   * most one character per unit. Fails when the count does not fit the placeholder units.
   */
  fun placeholderCount(baseLength: Int, branchVersion: LvVersion): Int {
    val unitSpan = branchVersion.unitSpan()
    require(unitSpan <= Int.MAX_VALUE - baseLength) {
      "A placeholder for a base of $baseLength chars and $unitSpan units overflows the unit space"
    }
    return baseLength + unitSpan
  }

  /**
   * Fails unless every new unit sits above every conflict unit. Without this check, a new delete
   * below a conflict delete would fail later in [DeleteTargets], with a message that names no cause.
   */
  private fun checkNewAboveConflict(conflict: ConflictRegion) {
    val conflictRanges = conflict.conflictRanges
    val newRanges = conflict.newRanges
    if (conflictRanges.isEmpty() || newRanges.isEmpty()) {
      return
    }
    val conflictEnd = conflictRanges.end(conflictRanges.size() - 1)
    require(newRanges.start(0) >= conflictEnd) {
      "The new units $newRanges do not all sit above the units $conflictRanges of the branch. " +
      "A merge into a branch must append the other history to the graph of that branch."
    }
  }
}
