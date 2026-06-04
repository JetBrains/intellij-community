// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.model

class MinimapLineProjection private constructor(
  val logicalLineCount: Int,
  val projectedLineCount: Int,
  val collapsedRegions: List<MinimapCollapsedRegion>,
  private val hiddenIntervals: List<MinimapHiddenInterval>,
  private val sourceSoftWrapsByLogicalLine: Map<Int, List<MinimapSourceSoftWrap>>,
  private val projectedStartsByLogicalLine: IntArray,
  private val projectedSpansByLogicalLine: IntArray,
  private val logicalLineByProjectedLine: IntArray,
) {
  fun logicalToProjectedLine(logicalLine: Int): Int? {
    if (logicalLine !in 0 until logicalLineCount) return null

    if (projectedSpansByLogicalLine[logicalLine] > 0) {
      return projectedStartsByLogicalLine[logicalLine]
    }

    val hiddenIntervalIndex = hiddenIntervalContaining(logicalLine)
    if (hiddenIntervalIndex >= 0) {
      return hiddenIntervals[hiddenIntervalIndex].projectedLine
    }

    return null
  }

  fun projectedToLogicalLine(projectedLine: Int): Int? {
    if (projectedLine !in 0 until projectedLineCount) return null
    return logicalLineByProjectedLine[projectedLine]
  }

  fun projectedLineSlotIndex(projectedLine: Int): Int? {
    val logicalLine = projectedToLogicalLine(projectedLine) ?: return null
    val projectedStart = projectedStartsByLogicalLine[logicalLine]
    val slotIndex = projectedLine - projectedStart
    return slotIndex.takeIf { it in 0 until projectedSpansByLogicalLine[logicalLine] }
  }

  fun sourceSoftWraps(logicalLine: Int): List<MinimapSourceSoftWrap>? {
    if (logicalLine !in 0 until logicalLineCount) return null
    return sourceSoftWrapsByLogicalLine[logicalLine]
  }

  fun isLineInCollapsedRegion(logicalLine: Int): Boolean {
    if (logicalLine !in 0 until logicalLineCount) return false
    if (collapsedRegions.isEmpty()) return false
    val regionIndex = collapsedRegionBeforeOrAt(logicalLine)
    return regionIndex >= 0 && logicalLine <= collapsedRegions[regionIndex].endLine
  }

  fun logicalToVisibleProjectedLine(logicalLine: Int): Int? {
    if (logicalLine !in 0 until logicalLineCount) return null
    return if (projectedSpansByLogicalLine[logicalLine] > 0) projectedStartsByLogicalLine[logicalLine] else null
  }

  fun logicalLineProjectedSpan(logicalLine: Int): Int {
    if (logicalLine !in 0 until logicalLineCount) return 0
    return projectedSpansByLogicalLine[logicalLine].coerceAtLeast(0)
  }

  private fun hiddenIntervalContaining(logicalLine: Int): Int {
    return containingIndex(hiddenIntervals, logicalLine, MinimapHiddenInterval::startLine, MinimapHiddenInterval::endLine)
  }

  private fun collapsedRegionBeforeOrAt(logicalLine: Int): Int {
    return lastIndexBeforeOrAt(collapsedRegions, logicalLine, MinimapCollapsedRegion::startLine)
  }

  private inline fun <T> containingIndex(
    elements: List<T>,
    value: Int,
    startOf: (T) -> Int,
    endOf: (T) -> Int,
  ): Int {
    var left = 0
    var right = elements.size - 1
    while (left <= right) {
      val mid = (left + right) ushr 1
      val element = elements[mid]
      when {
        value < startOf(element) -> right = mid - 1
        value > endOf(element) -> left = mid + 1
        else -> return mid
      }
    }
    return -1
  }

  private inline fun <T> lastIndexBeforeOrAt(
    elements: List<T>,
    value: Int,
    startOf: (T) -> Int,
  ): Int {
    var left = 0
    var right = elements.size - 1
    var result = -1
    while (left <= right) {
      val mid = (left + right) ushr 1
      if (startOf(elements[mid]) <= value) {
        result = mid
        left = mid + 1
      }
      else {
        right = mid - 1
      }
    }
    return result
  }

  companion object {
    fun identity(lineCount: Int): MinimapLineProjection = create(lineCount, emptyList(), emptyMap())

    fun create(
      logicalLineCount: Int,
      collapsedRegionsByLine: List<Pair<Int, Int>>,
      lineSpanOverrides: Map<Int, Int>,
      sourceSoftWrapsByLine: Map<Int, List<MinimapSourceSoftWrap>> = emptyMap(),
    ): MinimapLineProjection {
      val safeLogicalLineCount = logicalLineCount.coerceAtLeast(0)
      if (safeLogicalLineCount == 0) return emptyProjection()

      val projectedSpansByLogicalLine = IntArray(safeLogicalLineCount) { 1 }
      for ((logicalLine, rawSpan) in lineSpanOverrides) {
        if (logicalLine !in 0 until safeLogicalLineCount) continue
        projectedSpansByLogicalLine[logicalLine] = rawSpan.coerceAtLeast(1)
      }

      val acceptedCollapsedRegions = ArrayList<Pair<Int, Int>>(collapsedRegionsByLine.size)
      var lastCollapsedEndLine = -1
      for ((startLineRaw, endLineRaw) in collapsedRegionsByLine) {
        val startLine = startLineRaw.coerceIn(0, safeLogicalLineCount - 1)
        val endLine = endLineRaw.coerceIn(startLine, safeLogicalLineCount - 1)
        if (startLine <= lastCollapsedEndLine) continue

        acceptedCollapsedRegions += startLine to endLine
        lastCollapsedEndLine = endLine

        for (line in startLine + 1..endLine) {
          projectedSpansByLogicalLine[line] = 0
        }
      }

      val projectedStartsByLogicalLine = IntArray(safeLogicalLineCount)
      var projectedCursor = 0
      for (logicalLine in 0 until safeLogicalLineCount) {
        projectedStartsByLogicalLine[logicalLine] = projectedCursor
        val lineSpan = projectedSpansByLogicalLine[logicalLine]
        if (lineSpan > 0) {
          projectedCursor = (projectedCursor.toLong() + lineSpan.toLong())
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        }
      }

      val projectedLineCount = projectedCursor
      val logicalLineByProjectedLine = IntArray(projectedLineCount)
      for (logicalLine in 0 until safeLogicalLineCount) {
        val lineSpan = projectedSpansByLogicalLine[logicalLine]
        if (lineSpan <= 0) continue

        val projectedStart = projectedStartsByLogicalLine[logicalLine]
        if (projectedStart !in 0 until projectedLineCount) continue
        val projectedEndExclusive = (projectedStart + lineSpan).coerceAtMost(projectedLineCount)
        for (projectedLine in projectedStart until projectedEndExclusive) {
          logicalLineByProjectedLine[projectedLine] = logicalLine
        }
      }

      val hiddenIntervals = ArrayList<MinimapHiddenInterval>(acceptedCollapsedRegions.size)
      val collapsedRegions = ArrayList<MinimapCollapsedRegion>(acceptedCollapsedRegions.size)
      for ((startLine, endLine) in acceptedCollapsedRegions) {
        val hiddenStart = startLine + 1
        if (hiddenStart > endLine) continue

        val hiddenLineCount = endLine - hiddenStart + 1
        val projectedLine = if (projectedLineCount > 0) {
          projectedStartsByLogicalLine[startLine].coerceIn(0, projectedLineCount - 1)
        }
        else {
          0
        }
        hiddenIntervals += MinimapHiddenInterval(
          startLine = hiddenStart,
          endLine = endLine,
          projectedLine = projectedLine,
          hiddenLineCount = hiddenLineCount,
        )
        collapsedRegions += MinimapCollapsedRegion(
          startLine = startLine,
          endLine = endLine,
          projectedLine = projectedLine,
          hiddenLineCount = hiddenLineCount,
        )
      }

      return MinimapLineProjection(
        logicalLineCount = safeLogicalLineCount,
        projectedLineCount = projectedLineCount,
        collapsedRegions = collapsedRegions,
        hiddenIntervals = hiddenIntervals,
        sourceSoftWrapsByLogicalLine = sourceSoftWrapsByLine,
        projectedStartsByLogicalLine = projectedStartsByLogicalLine,
        projectedSpansByLogicalLine = projectedSpansByLogicalLine,
        logicalLineByProjectedLine = logicalLineByProjectedLine,
      )
    }

    private fun emptyProjection(): MinimapLineProjection {
      return MinimapLineProjection(
        logicalLineCount = 0,
        projectedLineCount = 0,
        collapsedRegions = emptyList(),
        hiddenIntervals = emptyList(),
        sourceSoftWrapsByLogicalLine = emptyMap(),
        projectedStartsByLogicalLine = IntArray(0),
        projectedSpansByLogicalLine = IntArray(0),
        logicalLineByProjectedLine = IntArray(0),
      )
    }
  }
}
