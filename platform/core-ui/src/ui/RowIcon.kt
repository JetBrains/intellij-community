// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LeakingThis")

package com.intellij.ui

import com.intellij.openapi.util.IconLoader.getDarkIcon
import com.intellij.ui.icons.IconReplacer
import com.intellij.ui.icons.IconWithToolTip
import com.intellij.ui.scale.ScaleType
import com.intellij.ui.scale.UserScaleContext
import com.intellij.util.IconUtil.copy
import com.intellij.util.ui.JBCachingScalableIcon
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon
import kotlin.math.ceil
import kotlin.math.max

open class RowIcon : JBCachingScalableIcon<RowIcon>, com.intellij.ui.icons.RowIcon, IconWithToolTip {
  private val alignment: com.intellij.ui.icons.RowIcon.Alignment
  private var width = 0
  private var height = 0
  private val icons: Array<Icon?>
  private var scaledIcons: Array<Icon?>?

  private var sizeIsDirty = true

  init {
    scaleContext.addUpdateListener(UserScaleContext.UpdateListener { updateSize() })
    setAutoUpdateScaleContext(false)
  }

  @JvmOverloads
  constructor(iconCount: Int, alignment: com.intellij.ui.icons.RowIcon.Alignment = com.intellij.ui.icons.RowIcon.Alignment.TOP) {
    this.alignment = alignment
    icons = arrayOfNulls(iconCount)
    scaledIcons = null
  }

  constructor(vararg icons: Icon?) : this(icons.size) {
    System.arraycopy(icons, 0, this.icons, 0, icons.size)
  }

  protected constructor(icon: RowIcon) : super(icon) {
    alignment = icon.alignment
    width = icon.width
    height = icon.height
    icons = icon.icons.clone()
    scaledIcons = null
  }

  override fun replaceBy(replacer: IconReplacer): RowIcon {
    val result = RowIcon(icon = this)
    for ((i, icon) in result.icons.withIndex()) {
      result.icons[i] = icon?.let { replacer.replaceIcon(it) }
    }
    return result
  }

  override fun copy() = RowIcon(icon = this)

  override fun deepCopy(): com.intellij.ui.icons.RowIcon {
    val result = RowIcon(this)
    for ((i, icon) in result.icons.withIndex()) {
      result.icons[i] = icon?.let { copy(it, null) }
    }
    return result
  }

  private fun getOrComputeScaledIcons(): Array<Icon?> {
    scaledIcons?.let {
      return it
    }
    return scaleIcons(icons = icons, scale = scale).also { scaledIcons = it }
  }

  override fun getAllIcons() = icons.filterNotNull()

  override fun hashCode() = icons.firstOrNull()?.hashCode() ?: 0

  override fun equals(other: Any?) = other === this || other is RowIcon && other.icons.contentEquals(icons)

  override fun getIconCount() = icons.size

  override fun setIcon(icon: Icon?, layer: Int) {
    icons[layer] = icon
    scaledIcons = null
  }

  override fun getIcon(index: Int) = icons[index]

  @Suppress("LocalVariableName")
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    scaleContext.update()
    if (sizeIsDirty) {
      updateSize()
    }
    var _x = x
    var _y: Int
    for (icon in getOrComputeScaledIcons()) {
      if (icon == null) {
        continue
      }

      _y = when (alignment) {
        com.intellij.ui.icons.RowIcon.Alignment.TOP -> y
        com.intellij.ui.icons.RowIcon.Alignment.CENTER -> y + (height - icon.iconHeight) / 2
        com.intellij.ui.icons.RowIcon.Alignment.BOTTOM -> y + (height - icon.iconHeight)
      }
      icon.paintIcon(c, g, _x, _y)
      _x += icon.iconWidth
      //_y += icon.getIconHeight();
    }
  }

  override fun getIconWidth(): Int {
    scaleContext.update()
    if (sizeIsDirty) {
      updateSize()
    }
    return ceil(scaleVal(width.toDouble(), ScaleType.OBJ_SCALE)).toInt()
  }

  override fun getIconHeight(): Int {
    scaleContext.update()
    if (sizeIsDirty) {
      updateSize()
    }
    return ceil(scaleVal(height.toDouble(), ScaleType.OBJ_SCALE)).toInt()
  }

  private fun updateSize() {
    sizeIsDirty = false

    var width = 0
    var height = 0
    for (icon in icons) {
      if (icon == null) {
        continue
      }

      width += icon.iconWidth
      //height += icon.getIconHeight();
      height = max(height, icon.iconHeight)
    }
    this.width = width
    this.height = height
  }

  override fun getDarkIcon(isDark: Boolean): Icon {
    val newIcon = copy()
    for ((i, icon) in newIcon.icons.withIndex()) {
      newIcon.icons[i] = icon?.let { getDarkIcon(it, isDark) }
    }
    return newIcon
  }

  override fun toString() = "RowIcon(icons=[${icons.joinToString(separator = ", ")}])"

  override fun getToolTip(composite: Boolean) = combineIconTooltips(icons)
}
