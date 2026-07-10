// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.lines

import andel.intervals.Interval
import andel.intervals.IntervalsQuery
import andel.text.TextRange

interface SoftWrapBuilder {
    fun buildSoftLines(
        text: CharSequence,
        inlays: IntervalsQuery<*, Inlay>,
        inlayMeasurer: (Inlay) -> Float,
        interlines: IntervalsQuery<*, Interline>,
        buildRange: TextRange,
        foldRanges: List<Interval<*, Fold>>,
        foldsMeasurer: (Fold) -> Float,
        lastLine: Boolean,
        targetWidth: Float = Float.POSITIVE_INFINITY,
        /** Font scale of the line being built, see [LineScale]: scales char widths and the wrap budget. */
        scale: Float = 1f,
    ): List<LineData>

    fun clone(width: Float, charWidth: Float): SoftWrapBuilder

    fun wrappingChanged(width: Float, charWidth: Float): Boolean
}

data class LineData(
  val length: Long,
  val interlineHeightAbove: LineBasedHeight,
  val interlineHeightBelow: LineBasedHeight,
  val width: Float,
  val ownHeight: LineBasedHeight = LineBasedHeight.ONE_LINE,
) {
  val totalHeight: LineBasedHeight
    get() = interlineHeightAbove + ownHeight + interlineHeightBelow
}
