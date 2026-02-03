// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.lines

import andel.intervals.Interval

interface LinesLayout {
  fun preferredWidth(): Float
  fun linesCount(): Long
  fun linesHeight(): LineBasedHeight

  fun lines(from: Long): Sequence<Line>
  fun line(top: LineBasedHeight): Line 
  fun line(offset: Long): Line
  fun nth(lineId: Long): Line
}

data class Line(
  val from: Long,
  val to: Long,
  val lineTop: LineBasedHeight,
  val totalHeight: LineBasedHeight,
  val interlineHeightAbove: LineBasedHeight,
  val interlineHeightBelow: LineBasedHeight,
  val lineIdx: Long,
  val width: Float,
) {
  operator fun contains(offset: Long): Boolean {
    @Suppress("ConvertTwoComparisonsToRangeCheck")
    return from <= offset && offset <= to
  }

  operator fun contains(interval: Interval<*, *>): Boolean {
    return interval.from in this && interval.to in this
  }
}

/*
* must be a Value
* */
interface Inlay {
  enum class Binding {
    BeforeRange, AfterRange
  }

  val binding: Binding
  val breaksDecorations: Boolean
    get() = true
}

val Interval<*, Inlay>.inlayOffset: Long
  get() = when (data.binding) {
    Inlay.Binding.BeforeRange -> from
    Inlay.Binding.AfterRange -> to
  }

interface Fold {
  val hasWidget: Boolean
  val defaultFolded: Boolean
  val focusable: Boolean
}

/*
* must be a Value
* */
interface Interline {
  enum class Binding {
    AboveLine, BelowLine
  }

  enum class Placement {
    ParentOverlay, Overlay, Inline,
  }

  val binding: Binding

  val placement: Placement get() = Placement.Overlay

  val paintOverGutter: Boolean get() = false

  val height: LineBasedHeight

  val focusable: Boolean

  val propagateEvents: Boolean
}

inline val Interval<*, Interline>.interlineOffset: Long
  get() = when (data.binding) {
    Interline.Binding.AboveLine -> from
    Interline.Binding.BelowLine -> to
  }

interface Postline {
  enum class Align { Left, Right }

  val align: Align
    get() = Align.Right
}

interface GutterWidget {
  val layer: Int get() = 1
  val replaceLineNumber: Boolean get() = true
  val width: Float get() = 0f

  val followOnScroll: Boolean get() = false
}
