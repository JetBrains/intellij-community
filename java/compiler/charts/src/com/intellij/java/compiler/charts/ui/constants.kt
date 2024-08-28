// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil.FontSize
import java.awt.Color

val COLOR_TEST_BLOCK: JBColor = JBColor(Color(0xc5e5cc), Color(0x375239))
val COLOR_TEST_BLOCK_DISABLED: JBColor = JBColor(Color(0xb6eedd), Color(0x26392b))
val COLOR_TEST_BORDER: JBColor = JBColor(Color(0x3b9954), Color(0x549159))
val COLOR_TEST_BORDER_SELECTED: JBColor = JBColor.GREEN

val COLOR_PRODUCTION_BLOCK: JBColor = JBColor(Color(0xc2d6fc), Color(0x2e436e))
val COLOR_PRODUCTION_BLOCK_DISABLED: JBColor = JBColor(Color(0xd7e4fd), Color(0x202e4d))
val COLOR_PRODUCTION_BORDER: JBColor = JBColor(Color(0x4781fa), Color(0x4978d6))
val COLOR_PRODUCTION_BORDER_SELECTED: JBColor = JBColor.BLUE

val COLOR_BACKGROUND: Color = JBColor.background()
val COLOR_BACKGROUND_ODD: JBColor = JBColor.WHITE
val COLOR_BACKGROUND_EVEN: JBColor = JBColor(Color(0xeeeeee), Color(0x3e4042))

val COLOR_TEXT: JBColor = JBColor(Color(0x1d1d1d), Color(0xbfbfbf))
val COLOR_LINE: JBColor = JBColor.LIGHT_GRAY

val COLOR_MEMORY: JBColor = JBColor.namedColor("Profiler.MemoryChart.inactiveBorderColor")
val COLOR_MEMORY_BORDER: JBColor = JBColor.namedColor("Profiler.MemoryChart.borderColor")

val COLOR_CPU: JBColor = JBColor.namedColor("Profiler.CpuChart.inactiveBorderColor")
val COLOR_CPU_BORDER: JBColor = JBColor.namedColor("Profiler.CpuChart.borderColor")


const val MODULE_BLOCK_PADDING: Double = 1.0
const val MODULE_BLOCK_BORDER: Double = 2.0

const val USAGE_BORDER: Float = 2.0f

val FONT_SIZE: FontSize = FontSize.NORMAL

const val AXIS_DISTANCE_PX: Int = 250
const val AXIS_MARKERS_COUNT: Int = 10
const val AXIS_TEXT_PADDING: Int = 2

const val MIN_ZOOM_SECONDS = 0.2
const val MAX_ZOOM_SECONDS = 1_000.0

const val MAX_WIDTH = 10_000.0

const val ZOOM_CACHING_DELAY = 50
const val BUFFERED_IMAGE_WIDTH_PX = 512
const val IMAGE_CACHE_ACTIVATION_COUNT = 8
const val REFRESH_TIMEOUT_SECONDS = 1L