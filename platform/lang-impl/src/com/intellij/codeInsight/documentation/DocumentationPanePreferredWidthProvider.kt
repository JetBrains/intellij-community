// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.html.cssBorderWidths
import com.intellij.util.ui.html.cssMargin
import com.intellij.util.ui.html.cssPadding
import com.intellij.util.ui.html.width
import javax.swing.text.StyleConstants
import javax.swing.text.View
import javax.swing.text.html.HTML
import javax.swing.text.html.InlineView
import kotlin.math.max
import kotlin.math.min

internal class DocumentationPanePreferredWidthProvider(
  private val rootView: View
) {

  private val cell2SubCells: MultiMap<View, View> = MultiMap.createSet()
  private val cellPreferredSectionWidth: HashMap<View, Int> = HashMap()
  private val viewWidthInsetMap: HashMap<View, Int> = HashMap()

  companion object {
    private const val MIN_CELL_WIDTH = 75
  }

  fun get(): Int {
    val views = findViews(rootView) { view ->
      val className = view.element.attributes.getAttribute(HTML.Attribute.CLASS) as? String
      val tagName = view.element.attributes.getAttribute(StyleConstants.NameAttribute) as? HTML.Tag
      // Let's select all relevant views, i.e. definitions, bottom sections, <pre>, <td> and <code>
      DocumentationMarkup.CLASS_DEFINITION == className || DocumentationMarkup.CLASS_BOTTOM == className
      || tagName === HTML.Tag.PRE || tagName === HTML.Tag.TD
      || (view is InlineView && view.cssPadding.width > 0 /* most likely <code> */)
    }
    if (views.isEmpty()) return -1

    val preferredWidthNoTables = views.maxOf { view ->
      var width = view.getPreferredSpan(View.X_AXIS).toInt()
      if (width < 0) return@maxOf -1
      if (isTd(view)) {
        // This is the main difference wrt to regular table layout.
        // We want table cells to have some minimum size, but not too large;
        // otherwise, tables would try to occupy as much space as possible to
        // render each cell in a single line. We want this behavior only with
        // code blocks or fragments.
        width = min(width, MIN_CELL_WIDTH)
      }
      width -= view.cssPadding.width
      var cur: View? = view
      // Let's accumulate ancestors insets width stopping at the root or <td> element
      while (cur != null) {
        width += getWidthInsets(cur)
        // We need to take special care of table cells to calculate table size correctly in case of code fragments and blocks
        if (isTd(cur)) {
          cellPreferredSectionWidth.merge(cur, width) { a, b -> Integer.max(a, b) }
          return@maxOf -1
        }
        cur = cur.parent
      }
      width
    }

    if (cellPreferredSectionWidth.isEmpty()) {
      // It looks like we don't have any tables
      return preferredWidthNoTables
    }

    // Let's build a cell dependency tree to be able to iterate over cells in a reasonable manner
    cellPreferredSectionWidth.keys.forEach {
      var cell = it
      var parent = cell.parent
      while (parent != null) {
        if (isTd(parent)) {
          if (cell2SubCells.getModifiable(parent).add(cell)) {
            cell = parent
          }
          else {
            return@forEach
          }
        }
        parent = parent.parent
      }
      cell2SubCells.putValue(null, cell)
    }

    // Let's start with cells, which do not have any cell ancestors
    return max(preferredWidthNoTables, getPreferredWidthForRows(null, cell2SubCells[null]))
  }

  private fun getPreferredWidthForRows(calculationsRoot: View?, cells: Collection<View>?): Int {
    if (cells.isNullOrEmpty()) return -1
    // Provided cells should have the same <td> as an ancestor, or no <td> at all

    // Now let's find out all the table rows we need to analyze
    val rows = cells.asSequence().mapNotNull { it.parent }.toSet()

    // Let's find out the maximum preferred size for any of the rows
    return rows.maxOf { row ->
      // Accumulate ancestors insets width till we reach calculations root view
      var width = 0
      var cur: View? = row
      while (cur != null && cur !== calculationsRoot) {
        width += getWidthInsets(cur)
        cur = cur.parent
      }
      // Return accumulated ancestors width plus sum of cells preferred widths
      width + IntRange(0, row.viewCount - 1)
        .map { row.getView(it) }
        .sumOf { getPreferredWidthForCell(it) }
    }
  }

  private fun getPreferredWidthForCell(cell: View): Int {
    // For a cell, the base preferred width is the maximum width calculated from child code fragments
    // or an actual preferred span no larger than MIN_CELL_WIDTH
    val preferredSectionWidth =
      cellPreferredSectionWidth[cell]
      ?: min(MIN_CELL_WIDTH, cell.getPreferredSpan(View.X_AXIS).toInt().takeIf { it > 0 }?.let { it + cell.cssMargin.width }
                             ?: MIN_CELL_WIDTH)

    // A cell can contain a table, so let's find out the maximum preferred size from any child table
    return max(preferredSectionWidth, getPreferredWidthForRows(cell.parent, cell2SubCells[cell]))
  }

  private fun getWidthInsets(view: View) =
    viewWidthInsetMap.computeIfAbsent(view) { it.cssMargin.width + it.cssPadding.width + it.cssBorderWidths.width }

  private fun isTd(view: View): Boolean {
    return view.element.attributes.getAttribute(StyleConstants.NameAttribute) == HTML.Tag.TD
  }

  private fun findViews(view: View, viewSelector: (View) -> Boolean): List<View> {
    val queue = ArrayList<View>()
    queue.add(view)

    val result = SmartList<View>()
    while (!queue.isEmpty()) {
      val cur = queue.removeAt(queue.size - 1)
      if (viewSelector(cur)) {
        result.add(cur)
      }
      for (i in 0 until cur.viewCount) {
        cur.getView(i)?.let { queue.add(it) }
      }
    }
    return result
  }

}