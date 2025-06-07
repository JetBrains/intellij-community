// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.AppUIUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics2D
import java.awt.TexturePaint
import kotlin.math.floor

@ApiStatus.Internal
class GradientTextureCache: Disposable {
  private var texture: TexturePaint? = null
  private var colorStart: Int = 0
  private var colorEnd: Int = 0
  private var x: Int = 0
  private var y: Int = 0

  @RequiresEdt
  fun getHorizontalTexture(graphics: Graphics2D, width: Int, colorStart: Color, colorEnd: Color, x: Int = 0, y: Int = 0): TexturePaint {
    val realWidth = floor(JBUIScale.sysScale(graphics) * width).toInt()

    return if (realWidth != texture?.image?.width || checkValues(colorStart, colorEnd, x, y)) {
      AppUIUtil.createHorizontalGradientTexture(graphics, colorStart, colorEnd, width, x, y).also {
        cacheValues(it, colorStart, colorEnd, x, y)
      }
    }
    else texture!!
  }

  @RequiresEdt
  fun getVerticalTexture(graphics: Graphics2D, height: Int, colorStart: Color, colorEnd: Color, x: Int = 0, y: Int = 0): TexturePaint {
    val realHeight = floor(JBUIScale.sysScale(graphics) * height).toInt()

    return if (realHeight != texture?.image?.height || checkValues(colorStart, colorEnd, x, y)) {
      AppUIUtil.createVerticalGradientTexture(graphics, colorStart, colorEnd, height, x, y).also {
        cacheValues(it, colorStart, colorEnd, x, y)
      }
    }
    else texture!!
  }

  private fun checkValues(colorStart: Color, colorEnd: Color, x: Int, y: Int): Boolean {
    return colorStart.rgb != this.colorStart || colorEnd.rgb != this.colorEnd || x != this.x || y != this.y
  }

  private fun cacheValues(paint: TexturePaint, colorStart: Color, colorEnd: Color, x: Int, y: Int) {
    texture = paint
    this.colorStart = colorStart.rgb
    this.colorEnd = colorEnd.rgb
    this.x = x
    this.y = y
  }

  override fun dispose() {
    texture = null
  }
}