// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.ide.troubleshooting

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.ScreenUtil
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.annotations.ApiStatus
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
      GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { screenInfo(it) }
    )
  }
}

private fun screenInfo(device: GraphicsDevice): ScreenInfo {
  val scale = JBUIScale.sysScale(device.defaultConfiguration)
  val displayMode = device.displayMode
  val resolutionScale = if (SystemInfo.isMac) {
    // On macOS, displayMode reports "fake" (scaled) resolution that matches user-space coordinates.
    // Scaling it will yield the correct resolution on a true retina display.
    // For "intermediate" displays, such as 27-32" 4K ones, it'll also be fake,
    // but at least it'll match, e.g., screenshot resolutions.
    // There seems to be no way to retrieve the actual resolution.
    // It isn't needed, though, as macOS performs raster scaling on its own.
    // Therefore, for all intents and purposes, a 32" 4K (3840x2160) monitor
    // set to 2560x1440 in the macOS settings is "actually" a 5K (5120x2880) monitor scaled at 200%,
    // as far as our code (both the platform and JBR) is concerned.
    scale
  }
  else {
    // On Windows, display modes are reliable and return the actual screen resolutions. No need to scale them.
    // On Linux, it's unpredictable and depends on the environment. In the best case, it works correctly,
    // otherwise it usually returns the scaled bounds, in the worst case without a way to unscale them
    // because it reports 200% scaling instead of the actual value.
    // So the best we can do here is to trust it and hope for the best.
    1.0f
  }
  val resolution = Resolution(scale(displayMode.width, resolutionScale), scale(displayMode.height, resolutionScale))
  val scaling = Scaling(scale)
  val bounds = device.defaultConfiguration.bounds
  val insets = ScreenUtil.getScreenInsets(device.defaultConfiguration)
  return ScreenInfo(resolution, scaling, bounds, insets)
}

fun scale(value: Int, scale: Float): Int = (value * scale).roundToInt()

internal data class ScreenInfo(
  val resolution: Resolution,
  val scaling: Scaling,
  val bounds: Rectangle,
  val insets: Insets,
)

internal data class Resolution(val width: Int, val height: Int) {
  override fun toString(): String = "${width}x${height}"
}

internal data class Scaling(val percentage: Int) {
  constructor(scale: Float) : this((scale * 100.0f).roundToInt())

  override fun toString(): String = "$percentage%"
}
