// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.troubleshooting

import com.intellij.ui.ScreenUtil
import com.intellij.ui.scale.JBUIScale
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Rectangle
import kotlin.math.roundToInt

internal data class DisplayInfo(
  val screens: List<ScreenInfo>,
) {
  companion object {
    @JvmStatic
    fun get(): DisplayInfo = DisplayInfo(
      GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { ScreenInfo(it) }
    )
  }
}

internal data class ScreenInfo(
  val resolution: Resolution,
  val scaling: Scaling,
  val bounds: Rectangle,
  val insets: Insets,
) {
  constructor(graphicsDevice: GraphicsDevice) : this(
    graphicsDevice.displayMode.let { Resolution(it.width, it.height) },
    Scaling(JBUIScale.sysScale(graphicsDevice.defaultConfiguration)),
    graphicsDevice.defaultConfiguration.bounds,
    ScreenUtil.getScreenInsets(graphicsDevice.defaultConfiguration),
  )
}

internal data class Resolution(val width: Int, val height: Int) {
  override fun toString(): String = "${width}x${height}"
}

internal data class Scaling(val percentage: Int) {
  constructor(scale: Float) : this((scale * 100.0f).roundToInt())

  override fun toString(): String = "$percentage%"
}
