// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.model

import kotlin.math.max

class MinimapLineProjection private constructor(
  val logicalLineCount: Int,
  val projectedLineCount: Int,
  val collapsedRegions: List<MinimapCollapsedRegion>,
  private val hiddenIntervals: List<MinimapHiddenInterval>,
  private val hiddenPrefixSums: IntArray,
  private val visibleSegments: List<MinimapVisibleSegment>,
) {


  fun logicalToProjectedLine(logicalLine: Int): Int? {
    if (logicalLine !in 0 until logicalLineCount) return null

    val hiddenIntervalIndex = hiddenIntervalContaining(logicalLine)
    if (hiddenIntervalIndex >= 0) {
      return hiddenIntervals[hiddenIntervalIndex].projectedLine
    }

    val hiddenBefore = hiddenLineCountBefore(logicalLine)
    return logicalLine - hiddenBefore
  }

  fun projectedToLogicalLine(projectedLine: Int): Int? {
    if (projectedLine !in 0 until projectedLineCount) return null

    val segmentIndex = visibleSegmentContaining(projectedLine)
    if (segmentIndex < 0) return null

    val segment = visibleSegments[segmentIndex]
    return segment.logicalStartLine + (projectedLine - segment.projectedStartLine)
  }

  fun isLineInCollapsedRegion(logicalLine: Int): Boolean {
    if (logicalLine !in 0 until logicalLineCount) return false
    if (collapsedRegions.isEmpty()) return false
    val regionIndex = collapsedRegionBeforeOrAt(logicalLine)
    return regionIndex >= 0 && logicalLine <= collapsedRegions[regionIndex].endLine
  }

  fun logicalToVisibleProjectedLine(logicalLine: Int): Int? {
    if (logicalLine !in 0 until logicalLineCount) return null
    if (hiddenIntervalContaining(logicalLine) >= 0) return null
    val hiddenBefore = hiddenLineCountBefore(logicalLine)
    return logicalLine - hiddenBefore
  }

  private fun hiddenIntervalContaining(logicalLine: Int): Int {
    return containingIndex(hiddenIntervals, logicalLine, MinimapHiddenInterval::startLine, MinimapHiddenInterval::endLine)
  }

  private fun hiddenLineCountBefore(logicalLine: Int): Int {
    if (hiddenIntervals.isEmpty()) return 0

    var left = 0
    var right = hiddenIntervals.size - 1
    var lastBefore = -1
    while (left <= right) {
      val mid = (left + right) ushr 1
      val interval = hiddenIntervals[mid]
      if (interval.endLine < logicalLine) {
        lastBefore = mid
        left = mid + 1
      }
      else {
        right = mid - 1
      }
    }

    if (lastBefore < 0) return 0
    return hiddenPrefixSums[lastBefore]
  }

  private fun visibleSegmentContaining(projectedLine: Int): Int {
    return containingIndex(visibleSegments, projectedLine, MinimapVisibleSegment::projectedStartLine, MinimapVisibleSegment::projectedEndLine)
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
    fun identity(lineCount: Int): MinimapLineProjection = create(lineCount, emptyList())

    fun create(logicalLineCount: Int, collapsedRegionsByLine: List<Pair<Int, Int>>): MinimapLineProjection {
      val safeLogicalLineCount = logicalLineCount.coerceAtLeast(0)
      if (safeLogicalLineCount == 0 || collapsedRegionsByLine.isEmpty()) {
        val visibleSegments = if (safeLogicalLineCount > 0) {
          listOf(
            MinimapVisibleSegment(
              logicalStartLine = 0,
              projectedStartLine = 0,
              lineCount = safeLogicalLineCount,
            ),
          )
        }
        else {
          emptyList()
        }

        return MinimapLineProjection(
          logicalLineCount = safeLogicalLineCount,
          projectedLineCount = safeLogicalLineCount,
          collapsedRegions = emptyList(),
          hiddenIntervals = emptyList(),
          hiddenPrefixSums = IntArray(0),
          visibleSegments = visibleSegments,
        )
      }

      val hiddenIntervals = ArrayList<MinimapHiddenInterval>(collapsedRegionsByLine.size)
      val collapsedRegions = ArrayList<MinimapCollapsedRegion>(collapsedRegionsByLine.size)
      var hiddenBefore = 0
      var lastCollapsedEndLine = -1

      for ((startLineRaw, endLineRaw) in collapsedRegionsByLine) {
        val startLine = startLineRaw.coerceIn(0, safeLogicalLineCount - 1)
        val endLine = endLineRaw.coerceIn(startLine, safeLogicalLineCount - 1)
        if (startLine <= lastCollapsedEndLine) continue
        val hiddenStart = startLine + 1
        if (hiddenStart > endLine) continue

        val hiddenLineCount = endLine - hiddenStart + 1
        val projectedLine = (startLine - hiddenBefore).coerceAtLeast(0)
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
        hiddenBefore += hiddenLineCount
        lastCollapsedEndLine = endLine
      }

      val projectedLineCount = max(safeLogicalLineCount - hiddenBefore, 0)
      val visibleSegments = buildVisibleSegments(safeLogicalLineCount, hiddenIntervals)
      val hiddenPrefixSums = IntArray(hiddenIntervals.size)
      var prefix = 0
      for (index in hiddenIntervals.indices) {
        prefix += hiddenIntervals[index].hiddenLineCount
        hiddenPrefixSums[index] = prefix
      }
      return MinimapLineProjection(
        logicalLineCount = safeLogicalLineCount,
        projectedLineCount = projectedLineCount,
        collapsedRegions = collapsedRegions,
        hiddenIntervals = hiddenIntervals,
        hiddenPrefixSums = hiddenPrefixSums,
        visibleSegments = visibleSegments,
      )
    }

    private fun buildVisibleSegments(logicalLineCount: Int, hiddenIntervals: List<MinimapHiddenInterval>): List<MinimapVisibleSegment> {
      if (logicalLineCount <= 0) return emptyList()
      if (hiddenIntervals.isEmpty()) {
        return listOf(
          MinimapVisibleSegment(
            logicalStartLine = 0,
            projectedStartLine = 0,
            lineCount = logicalLineCount,
          ),
        )
      }

      val result = ArrayList<MinimapVisibleSegment>(hiddenIntervals.size + 1)
      var logicalCursor = 0
      var projectedCursor = 0
      for (hidden in hiddenIntervals) {
        val visibleEnd = hidden.startLine - 1
        if (logicalCursor <= visibleEnd) {
          val lineCount = visibleEnd - logicalCursor + 1
          result += MinimapVisibleSegment(
            logicalStartLine = logicalCursor,
            projectedStartLine = projectedCursor,
            lineCount = lineCount,
          )
          projectedCursor += lineCount
        }
        logicalCursor = hidden.endLine + 1
      }

      if (logicalCursor <= logicalLineCount - 1) {
        val lineCount = logicalLineCount - logicalCursor
        result += MinimapVisibleSegment(
          logicalStartLine = logicalCursor,
          projectedStartLine = projectedCursor,
          lineCount = lineCount,
        )
      }

      return result
    }
  }
}
