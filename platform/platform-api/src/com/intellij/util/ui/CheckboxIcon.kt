// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.ui.ExperimentalUI
import com.intellij.ui.SizedIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.intellij.util.PlatformIcons
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Component
import java.awt.Graphics

@ApiStatus.Internal
object CheckboxIcon {
  @JvmStatic
  fun create(
    color: Color,
  ): ColorIcon {
    val size = iconSize
    return ColorIcon(size, size, size, size, color, false, arcSize)
  }

  @JvmStatic
  fun createAndScale(color: Color): ColorIcon = JBUIScale.scaleIcon(create(color))

  @JvmStatic
  fun createAndScaleCheckbox(color: Color): JBScalableIcon {
    return createAndScaleCheckbox(color, true)
  }

  @JvmStatic
  fun createAndScaleCheckbox(color: Color, isSelected: Boolean): JBScalableIcon {
    if (isSelected) {
      return JBUIScale.scaleIcon(WithColor(iconSize, color, arcSize))
    }
    else {
      return JBUIScale.scaleIcon(ColorIcon(iconSize, iconSize, iconSize, iconSize, color, false, arcSize))
    }
  }

  private const val iconSize = 14
  private const val checkMarkSize = 12

  private val arcSize
    get() = if (ExperimentalUI.isNewUI()) 4 else 0


  class WithColor(private val size: Int, color: Color, arc: Int) : ColorIcon(size, size, size, size, color, false, arc) {
    private var mySizedIcon: SizedIcon

    init {
      val icon = if (ExperimentalUI.isNewUI()) {
        IconUtil.resizeSquared(LafIconLookup.getIcon("checkmark", true), checkMarkSize)
      }
      else {
        PlatformIcons.CHECK_ICON_SMALL
      }
      mySizedIcon = SizedIcon(icon, checkMarkSize, checkMarkSize)
    }

    override fun withIconPreScaled(preScaled: Boolean): WithColor {
      mySizedIcon = mySizedIcon.withIconPreScaled(preScaled) as SizedIcon
      return super.withIconPreScaled(preScaled) as WithColor
    }

    override fun paintIcon(component: Component, g: Graphics, i: Int, j: Int) {
      super.paintIcon(component, g, i, j)

      val offset = (iconWidth - mySizedIcon.iconWidth) / 2
      mySizedIcon.paintIcon(component, g, i + offset, j + offset)
    }

    override fun copy(): ColorIcon {
      return WithColor(size, iconColor, arcSize)
    }
  }
}
