// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText

/**
 * The greatest number of concurrent runs that one row draws.
 */
private const val MAX_RUNS_PER_ROW = 6

/**
 * The spaces between two boxes that stand side by side.
 */
private const val GAP = 2

private const val NO_COLUMN = -1

/**
 * The slot of the base box, when a layout draws one.
 */
private const val BASE_SLOT = 0

/**
 * The [runs] placed in rows and columns, and the box art for them.
 *
 * A run continues the column of its first parent when two things hold. It is the first run to do
 * so, and no other child of that parent sits deeper. A deeper child needs the column for its line,
 * so the run then takes a free column. Every other run takes a free column too, which is what moves
 * a second branch aside. A column stays reserved until the deepest child of its runs, so two
 * branches never share one.
 *
 * [runs] holds the run indexes to draw, ascending. Every array below indexes a SLOT and not a run
 * of the graph. So a diagram that cuts the middle of a history stays as small as what it draws. A
 * parent outside [runs] counts for nothing, so a run whose parents are all outside starts at the
 * depth 0.
 *
 * The BASE BOX holds the base text and the base version of the graph, and it takes the slot 0.
 * Every run with no parent follows it, so it sits alone in the first row. Only a layout that starts
 * at the first run draws it. A new graph with an empty base text gets none.
 */
internal class DiagramLayout(private val graph: EventGraphImpl, private val runs: List<Int>) {
  private val drawsBase = startsAtFirstRun() && hasBaseBox()

  /**
   * The slot of the first run: 1 after the base box, and 0 without one.
   */
  private val firstRunSlot = if (drawsBase) BASE_SLOT + 1 else BASE_SLOT
  private val slotCount = firstRunSlot + runs.size

  /**
   * The slot of each drawn run, by its run index.
   */
  private val slots = HashMap<Int, Int>(runs.size)
  private val depths = IntArray(slotCount)
  private val rows = ArrayList<MutableList<Int>>()
  private val hiddenInRow = ArrayList<Int>()
  private val children = Array(slotCount) { ArrayList<Int>() }
  private val parents = Array(slotCount) { emptyList<Int>() }
  private val columns = IntArray(slotCount) { NO_COLUMN }
  private val bodies = arrayOfNulls<List<String>>(slotCount)
  private val widths: IntArray
  private val middles: IntArray
  private val starts: IntArray
  private val width: Int

  init {
    for ((index, run) in runs.withIndex()) {
      slots[run] = firstRunSlot + index
    }
    buildRows()
    buildLinks()
    buildColumns()
    widths = widths()
    starts = IntArray(widths.size)
    middles = IntArray(widths.size)
    var offset = 0
    for (column in widths.indices) {
      starts[column] = offset
      middles[column] = offset + widths[column] / 2
      offset += widths[column] + GAP
    }
    width = if (widths.isEmpty()) 0 else starts[widths.size - 1] + widths[widths.size - 1]
  }

  fun appendTo(text: StringBuilder) {
    for (row in rows.indices) {
      if (row > 0) {
        appendConnector(text, row - 1)
      }
      appendRow(text, row)
    }
  }

  // --------------------------------------------------------------------------------- layout

  /**
   * Whether [runs] starts at the first run of the graph. A graph with no run starts there too.
   */
  private fun startsAtFirstRun(): Boolean {
    return runs.isEmpty() || runs[0] == 0
  }

  /**
   * Whether the graph has a base box, in this layout or in one above it.
   */
  private fun hasBaseBox(): Boolean {
    return graph.baseText().length() > 0 || !graph.baseVersion().isRoot()
  }

  private fun isBase(slot: Int): Boolean {
    return slot < firstRunSlot
  }

  /**
   * The run of the [slot], which must not be the base box.
   */
  private fun runOf(slot: Int): StoredRun {
    return graph.runByIndex(runs[slot - firstRunSlot])
  }

  /**
   * The slot of the run that holds the parent unit [parent], or `null` when it is not drawn.
   */
  private fun parentSlot(parent: LV): Int? {
    return slots[graph.runIndexOf(parent)]
  }

  /**
   * The slots of the parents of the [slot], drawn or hidden by the row bound. A run with no parent
   * follows the base box when this layout draws one.
   */
  private fun parentSlotsOf(slot: Int): List<Int> {
    if (isBase(slot)) {
      return emptyList()
    }
    val runParents = runOf(slot).runParents()
    if (runParents.isEmpty() && drawsBase) {
      return listOf(BASE_SLOT)
    }
    val parentSlots = ArrayList<Int>(runParents.size)
    for (parent in runParents) {
      val parentSlot = parentSlot(parent) ?: continue
      parentSlots.add(parentSlot)
    }
    return parentSlots
  }

  /**
   * The lines of the box of the [slot].
   */
  private fun contentOf(slot: Int): List<String> {
    return if (isBase(slot)) {
      baseBody(graph.baseText(), graph.baseVersion())
    } else {
      body(runOf(slot))
    }
  }

  /**
   * Groups the runs by depth, and drops the ones that [MAX_RUNS_PER_ROW] leaves out.
   */
  private fun buildRows() {
    for (slot in 0 until slotCount) {
      var depth = 0
      for (parentSlot in parentSlotsOf(slot)) {
        depth = maxOf(depth, depths[parentSlot] + 1)
      }
      depths[slot] = depth
      while (rows.size <= depth) {
        rows.add(ArrayList())
        hiddenInRow.add(0)
      }
      if (rows[depth].size < MAX_RUNS_PER_ROW) {
        rows[depth].add(slot)
        bodies[slot] = contentOf(slot)
      } else {
        hiddenInRow[depth] = hiddenInRow[depth] + 1
      }
    }
  }

  /**
   * Links the drawn runs to the drawn parents and children. A dropped run links to none.
   */
  private fun buildLinks() {
    for (row in rows) {
      for (slot in row) {
        val found = ArrayList<Int>()
        for (parentSlot in parentSlotsOf(slot)) {
          if (bodies[parentSlot] != null && !found.contains(parentSlot)) {
            found.add(parentSlot)
            children[parentSlot].add(slot)
          }
        }
        parents[slot] = found
      }
    }
  }

  private fun buildColumns() {
    val tips = ArrayList<Int>()
    val reserved = ArrayList<Int>()
    for (row in rows.indices) {
      for (slot in rows[row]) {
        val first = parents[slot].firstOrNull() ?: NO_COLUMN
        val inherited = if (first == NO_COLUMN) NO_COLUMN else columns[first]
        // A deeper child of the first parent draws its line down this column. So the run takes the
        // column only when no such child exists.
        val takesParentColumn = inherited != NO_COLUMN &&
                                tips[inherited] == first &&
                                deepestChild(first) == depths[slot]
        val column = if (takesParentColumn) inherited else freeColumn(reserved, row, tips)
        tips[column] = slot
        columns[slot] = column
        reserved[column] = maxOf(reserved[column], row, deepestChild(slot))
      }
    }
  }

  private fun freeColumn(reserved: MutableList<Int>, row: Int, tips: MutableList<Int>): Int {
    for (column in reserved.indices) {
      if (reserved[column] < row) {
        return column
      }
    }
    reserved.add(row)
    tips.add(NO_COLUMN)
    return reserved.size - 1
  }

  private fun deepestChild(slot: Int): Int {
    var deepest = depths[slot]
    for (child in children[slot]) {
      deepest = maxOf(deepest, depths[child])
    }
    return deepest
  }

  private fun widths(): IntArray {
    var count = 0
    for (slot in 0 until slotCount) {
      count = maxOf(count, columns[slot] + 1)
    }
    val widths = IntArray(count)
    for (slot in 0 until slotCount) {
      val body = bodies[slot] ?: continue
      // A box holds one space of padding, a border, and the widest line of its body.
      val box = body.maxOf { it.length } + 4
      widths[columns[slot]] = maxOf(widths[columns[slot]], box)
    }
    return widths
  }

  // ---------------------------------------------------------------------------------- boxes

  private fun appendRow(text: StringBuilder, row: Int) {
    val height = bodyOf(rows[row][0]).size + 2
    for (line in 0 until height) {
      val chars = CharArray(width) { ' ' }
      for (column in widths.indices) {
        val slot = rows[row].firstOrNull { columns[it] == column }
        if (slot != null) {
          boxLine(slot, line).toCharArray(chars, starts[column])
        } else if (passesThrough(column, row)) {
          chars[middles[column]] = '│'
        }
      }
      text.append('\n').append(String(chars).trimEnd())
    }
    if (hiddenInRow[row] > 0) {
      text.append("\n... ").append(runsWord(hiddenInRow[row], "concurrent")).append(" ...")
    }
  }

  /**
   * The line [line] of the box of the [slot], as wide as its column.
   */
  private fun boxLine(slot: Int, line: Int): String {
    val body = bodyOf(slot)
    val boxWidth = widths[columns[slot]]
    if (line == 0) {
      return border(boxWidth, '┌', '┐', if (hasParent(slot)) '┴' else '─')
    }
    if (line > body.size) {
      return border(boxWidth, '└', '┘', if (hasChild(slot)) '┬' else '─')
    }
    val inner = body[line - 1]
    return "│ " + inner + " ".repeat(boxWidth - inner.length - 4) + " │"
  }

  /**
   * The lines of the box of the [slot]. Only a drawn run has a box.
   */
  private fun bodyOf(slot: Int): List<String> {
    val body = bodies[slot]
    require(body != null) {
      "The run of the slot $slot has no box, because its row hid it"
    }
    return body
  }

  /**
   * Whether a line reaches the top of the box of the [slot], which the `┴` there shows.
   *
   * A line comes from a drawn parent. A run at the depth 0 with parents lost them to the cut of
   * the diagram. Its `┴` joins the count line above the segment. Below the count line, a run with
   * no parent follows the base box above it, and it joins the count line too. A parent that the row
   * bound hid draws no line, so a run whose only parents it hid keeps a plain border.
   */
  private fun hasParent(slot: Int): Boolean {
    return parents[slot].isNotEmpty() || hasParentAboveCountLine(slot)
  }

  /**
   * Whether the parents of the [slot] sit above the count line of the diagram.
   */
  private fun hasParentAboveCountLine(slot: Int): Boolean {
    if (isBase(slot) || depths[slot] > 0) {
      return false
    }
    return runOf(slot).runParents().isNotEmpty() || hasBaseBox()
  }

  /**
   * Whether the run of the [slot] is followed by another run of the GRAPH, drawn or not.
   *
   * A version names every unit that has no child, so a run whose last unit is absent from
   * the version has one. That test is cheap, and it needs no scan over the runs that come
   * after this one. It misses a child that names a unit inside the run and not its last, so
   * the drawn children answer first.
   */
  private fun hasChild(slot: Int): Boolean {
    if (children[slot].isNotEmpty()) {
      return true
    }
    // The first run of a graph has no parent, and a layout with the base box always draws it. So
    // the drawn children of a base box are the whole answer.
    if (isBase(slot)) {
      return false
    }
    return !graph.lvVersion().contains(runOf(slot).lvEnd() - 1)
  }

  /**
   * A box border of [boxWidth], with [middle] where a line joins it.
   */
  private fun border(
    boxWidth: Int,
    left: Char,
    right: Char,
    middle: Char,
  ): String {
    val chars = CharArray(boxWidth) { '─' }
    chars[0] = left
    chars[boxWidth - 1] = right
    chars[boxWidth / 2] = middle
    return String(chars)
  }

  /**
   * Whether a line crosses the row [row] in the column [column] without a box.
   *
   * A branch that makes fewer edits than its neighbour waits for its next run. The column carries
   * the line down through the rows where the branch has no box.
   */
  private fun passesThrough(column: Int, row: Int): Boolean {
    for (slot in 0 until slotCount) {
      if (columns[slot] != column || depths[slot] >= row) {
        continue
      }
      for (child in children[slot]) {
        if (depths[child] > row) {
          return true
        }
      }
    }
    return false
  }

  // ----------------------------------------------------------------------------- connectors

  /**
   * Appends the lines between the row [row] and the row below it.
   *
   * A straight run of lines takes one line. A row that opens or closes a branch takes three lines.
   * They are the lines that come down, the row that joins the columns, and the lines that go on.
   * The joining row is drawn from the four directions each position connects to, so every corner
   * and tee comes out right whatever the shape.
   *
   * A sideways line can pass a column that it does not join. The `│` of that column then stays
   * whole and the sideways line breaks at it, so a crossing never looks like a `┼` join.
   */
  private fun appendConnector(text: StringBuilder, row: Int) {
    val below = row + 1
    val up = BooleanArray(widths.size)
    val down = BooleanArray(widths.size)
    val left = BooleanArray(width)
    val right = BooleanArray(width)
    // The columns that a sideways line starts or ends at. Every other column it passes is a crossing.
    val joins = BooleanArray(widths.size)
    var joined = false
    for (slot in rows[below]) {
      for (parent in parents[slot]) {
        val from = columns[parent]
        val to = columns[slot]
        up[from] = true
        if (from == to) {
          down[to] = true
        } else {
          down[to] = true
          joined = true
          joins[from] = true
          joins[to] = true
          for (x in minOf(middles[from], middles[to]) until maxOf(middles[from], middles[to])) {
            right[x] = true
            left[x + 1] = true
          }
        }
      }
    }
    // A line that only crosses this gap keeps its column on both sides.
    for (column in widths.indices) {
      if (passesThrough(column, below)) {
        up[column] = true
        down[column] = true
      }
    }
    if (!joined) {
      text.append('\n').append(verticals(up, down))
      return
    }
    text.append('\n').append(verticals(up, BooleanArray(widths.size) { true }))
    text.append('\n').append(junction(up, down, left, right, joins))
    text.append('\n').append(verticals(BooleanArray(widths.size) { true }, down))
  }

  /**
   * One line with a `│` in every column that both [up] and [down] name.
   */
  private fun verticals(up: BooleanArray, down: BooleanArray): String {
    val chars = CharArray(width) { ' ' }
    for (column in widths.indices) {
      if (up[column] && down[column]) {
        chars[middles[column]] = '│'
      }
    }
    return String(chars).trimEnd()
  }

  /**
   * The line that joins the columns, one character per connected direction. A column that a
   * sideways line crosses but does not [joins] keeps its plain `│`.
   */
  private fun junction(
    up: BooleanArray,
    down: BooleanArray,
    left: BooleanArray,
    right: BooleanArray,
    joins: BooleanArray,
  ): String {
    val chars = CharArray(width) { ' ' }
    for (x in 0 until width) {
      val column = widths.indices.firstOrNull { middles[it] == x }
      val crossedOnly = column != null &&
                        !joins[column] &&
                        up[column] &&
                        down[column]
      if (crossedOnly) {
        chars[x] = '│'
        continue
      }
      val mask = (if (column != null && up[column]) 8 else 0) or
                 (if (column != null && down[column]) 4 else 0) or
                 (if (left[x]) 2 else 0) or
                 (if (right[x]) 1 else 0)
      chars[x] = JUNCTIONS[mask]
    }
    return String(chars).trimEnd()
  }

  private companion object {
    /**
     * The character for each set of connected directions, as `up|down|left|right` bits.
     * The index 0 is a gap, and a single direction keeps the line going.
     */
    private val JUNCTIONS = charArrayOf(
      ' ', '─', '─', '─', // ....  ...R  ..L.  ..LR
      '│', '┌', '┐', '┬', // .D..  .D.R  .DL.  .DLR
      '│', '└', '┘', '┴', // U...  U..R  U.L.  U.LR
      '│', '├', '┤', '┼', // UD..  UD.R  UDL.  UDLR
    )
  }
}

/**
 * The lines of one box, one field per line.
 */
private fun body(run: StoredRun): List<String> {
  val event = run.event
  val op = event.op()
  val head = when (op) {
    is DocumentOp.Insert -> "insert ${op.fragment().quotedForMessage()}"
    is DocumentOp.Delete -> "delete ${op.length()} ${if (op.length() == 1) "char" else "chars"}"
  }
  // A name is free text, so it gets the quoting of a fragment: it can neither break the box nor
  // widen it past the bound.
  return listOf(
    head,
    "offset ${op.offset()}",
    "lv ${lvs(run)}",
    "agent ${event.agent().toString().quotedForMessage()}",
  )
}

/**
 * The lines of the base box: the base [text], and the base [version] when it is not the root.
 */
private fun baseBody(text: DocumentText, version: VersionImpl): List<String> {
  val textLine = "base ${text.chars().quotedForMessage()}"
  if (version.isRoot()) {
    return listOf(textLine)
  }
  return listOf(textLine, "version ${version.quotedForMessage()}")
}

/**
 * The lv range of [run]: one lv for a run of one unit, and `first..last` for a longer one.
 */
private fun lvs(run: StoredRun): String {
  val last = run.lvEnd() - 1
  return if (run.lvStart == last) "${run.lvStart}" else "${run.lvStart}..$last"
}

/**
 * A count of runs with the right noun: "1 more run", "2 more runs".
 */
internal fun runsWord(count: Int, kind: String = ""): String {
  val noun = if (count == 1) "run" else "runs"
  return if (kind.isEmpty()) "$count more $noun" else "$count more $kind $noun"
}
