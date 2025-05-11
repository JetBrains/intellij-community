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
  private var colorStart: Color? = null
  private var colorEnd: Color? = null
  private var x: Int = 0
  private var y: Int = 0

  @RequiresEdt
  fun getHorizontalTexture(graphics: Graphics2D, width: Int, colorStart: Color, colorEnd: Color, x: Int = 0, y: Int = 0): TexturePaint {
    val realWidth = floor(JBUIScale.sysScale(graphics) * width).toInt()
    return if (realWidth != texture?.image?.width || colorStart != this.colorStart || colorEnd != this.colorEnd || x != this.x || y != this.y) {
      AppUIUtil.createHorizontalGradientTexture(graphics, colorStart, colorEnd, width, x, y).also {
        texture = it
        this.colorStart = colorStart
        this.colorEnd = colorEnd
        this.x = x
        this.y = y
      }
    }
    else texture!!
  }

  @RequiresEdt
  fun getVerticalTexture(graphics: Graphics2D, height: Int, colorStart: Color, colorEnd: Color, x: Int = 0, y: Int = 0): TexturePaint {
    val realHeight = floor(JBUIScale.sysScale(graphics) * height).toInt()
    return if (realHeight != texture?.image?.height || colorStart != this.colorStart || colorEnd != this.colorEnd || x != this.x || y != this.y) {
      AppUIUtil.createVerticalGradientTexture(graphics, colorStart, colorEnd, height, x, y).also {
        texture = it
        this.colorStart = colorStart
        this.colorEnd = colorEnd
        this.x = x
        this.y = y
      }
    }
    else texture!!
  }

  override fun dispose() {
    texture = null
  }
}