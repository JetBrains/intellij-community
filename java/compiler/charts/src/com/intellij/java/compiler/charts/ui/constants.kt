// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil.FontSize
import java.util.concurrent.TimeUnit

object Colors {
  object Background {
    val ODD: JBColor = exact("CompilationCharts.background.odd")
    val EVEN: JBColor = exact("CompilationCharts.background.even")
    val DEFAULT: JBColor = exact("CompilationCharts.background.default")

    fun getRowColor(row: Int): JBColor = if (row % 2 == 0) EVEN else ODD
  }

  @Suppress("PropertyName")
  interface Block {
    val ENABLED: JBColor
    val DISABLED: JBColor
    val BORDER: JBColor
    val SELECTED: JBColor
  }

  object Test : Block {
    override val ENABLED: JBColor = exact("CompilationCharts.test.enabled")
    override val DISABLED: JBColor = exact("CompilationCharts.test.disabled")
    override val BORDER: JBColor = exact("CompilationCharts.test.stroke")
    override val SELECTED: JBColor = exact("CompilationCharts.test.selected")
  }

  object Production : Block {
    override val ENABLED: JBColor = exact("CompilationCharts.production.enabled")
    override val DISABLED: JBColor = exact("CompilationCharts.production.disabled")
    override val BORDER: JBColor = exact("CompilationCharts.production.stroke")
    override val SELECTED: JBColor = exact("CompilationCharts.production.selected")
  }

  @Suppress("PropertyName")
  interface Usage {
    val BACKGROUND: JBColor
    val BORDER: JBColor
  }

  object Memory: Usage {
    override val BACKGROUND: JBColor = exact("CompilationCharts.memory.background")
    override val BORDER: JBColor = exact("CompilationCharts.memory.stroke")
  }

  object Cpu: Usage {
    override val BACKGROUND: JBColor = exact("CompilationCharts.cpu.background")
    override val BORDER: JBColor = exact("CompilationCharts.cpu.stroke")
  }

  val LINE: JBColor = exact("CompilationCharts.lineColor")
  val TEXT: JBColor = exact("CompilationCharts.textColor")

  fun getBlock(isTest: Boolean): Block = if (isTest) Test else Production

  private fun exact(propertyName: String): JBColor {
    return JBColor.namedColor(propertyName)
  }

  private fun JBColor.alpha(alpha: Double): JBColor {
    return ColorUtil.withAlpha(this, alpha) as JBColor
  }
}

object Settings {
  object Block {
    const val PADDING: Double = 1.0
    const val BORDER: Double = 2.0
    val HEIGHT = JBTable().rowHeight * 1.5
  }

  object Axis {
    const val DISTANCE: Int = 250
    const val MARKERS_COUNT: Int = 10
    const val TEXT_PADDING: Int = 2
  }

  object Zoom {
    const val SCALE: Double = 24.0
    const val IN: Double = 1.25
    const val OUT: Double = 0.8
  }

  object Usage {
    const val BORDER: Float = 2.0f
  }

  object Font {
    val SIZE: FontSize = FontSize.NORMAL
  }

  object Image {
    const val CACHE_ACTIVATION_COUNT: Int = 8
    const val WIDTH: Int = 512
  }

  interface Timeout {
    val timeout: Long
    val unit: TimeUnit
  }

  object Refresh: Timeout {
    override val timeout: Long = 1L
    override val unit: TimeUnit = TimeUnit.SECONDS
  }

  object Scroll: Timeout {
    override val timeout: Long = 100L
    override val unit: TimeUnit = TimeUnit.MILLISECONDS
  }

  object Toolbar {
    const val ID = "CompilationChartsToolbar"
  }

  object Popup {
    val MODULE_IMAGE = AllIcons.Actions.ModuleDirectory
  }

  //object
}