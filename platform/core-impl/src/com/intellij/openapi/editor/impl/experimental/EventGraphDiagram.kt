// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

/**
 * Draws an [EventGraphImpl] as a box diagram. One box is one run, and a line between two
 * boxes says that the lower run follows the upper one.
 *
 * ```
 * ┌───────────────┐
 * │ insert "abc"  │
 * │ offset 0      │
 * │ lv 0..2       │
 * │ agent "user1" │
 * └───────┬───────┘
 *         │
 *         ├──────────────────┐
 *         │                  │
 * ┌───────┴───────┐  ┌───────┴───────┐
 * ```
 *
 * A box holds one field per line, so a box stays narrow and several branches fit side by
 * side. A branch keeps one column for as long as it runs, and the column stays reserved
 * while the branch waits for its next run, so a `│` never changes owner.
 *
 * One row of boxes holds every run at one depth, where the depth of a run is one more than the
 * deepest of its parents. So two boxes in one row are always concurrent. An ancestor has a smaller
 * depth than its descendant, so it lands in an earlier row.
 *
 * Two things the diagram cannot show. A parent unit can sit inside a run, and a line reaches the
 * box of the run and not the unit. A run also keeps at least one parent in the row above it,
 * because its depth follows its deepest parent. An older parent gets its line drawn from its own
 * column and not from its own box.
 *
 * The diagram is a debugging aid and it reaches the log, so it is bounded. A long history loses its
 * MIDDLE and not its end. The diagram draws the first [HEAD_RUNS] runs, then the count of what it
 * dropped, then the newest runs. So it always shows where the document started and what happened
 * last, which is what a failure asks about. A box costs six lines, so see [MAX_RUNS], and
 * [DiagramLayout] for the bound of a row.
 *
 * The runs below the count line hold parents that the diagram dropped. A `┴` there joins the
 * count line and not a box, and the depth of such a run starts again at the count line.
 *
 * A graph draws its base text and its base version in a base box above the first run. A new graph
 * with an empty base text has no base box. Every run with no parent follows the base box.
 */
internal object EventGraphDiagram {

  /**
   * The greatest number of runs that one diagram draws. A box costs six lines. The base box does
   * not count here, and it adds at most five lines.
   */
  private const val MAX_RUNS = 40

  /**
   * The number of runs that a diagram keeps at the start of the history when it cuts the middle.
   */
  private const val HEAD_RUNS = 2

  fun render(graph: EventGraphImpl): String {
    val count = graph.runCount()
    val text = StringBuilder()
    text.append("EventGraph(units=").append(graph.size())
      .append(", runs=").append(count)
      .append(", version=").append(graph.lvVersion())
      .append(')')
    if (count <= MAX_RUNS) {
      // A graph with no run still draws its base box.
      DiagramLayout(graph, (0 until count).toList()).appendTo(text)
      return text.toString()
    }
    // The two segments are laid out on their own. The runs between them are gone, so no column can
    // carry a line across the gap.
    DiagramLayout(graph, (0 until HEAD_RUNS).toList()).appendTo(text)
    text.append("\n... ").append(runsWord(count - MAX_RUNS)).append(" ...")
    DiagramLayout(graph, (count - (MAX_RUNS - HEAD_RUNS) until count).toList()).appendTo(text)
    return text.toString()
  }
}
