// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.RetrievableIcon
import org.jetbrains.annotations.Contract
import java.awt.Component
import java.awt.Graphics
import java.util.function.Supplier
import javax.swing.Icon

open class IconWrapperWithToolTip : IconWithToolTip, CopyableIcon, RetrievableIcon {
  private val icon: Icon
  private val toolTip: Supplier<String>

  constructor(icon: Icon, toolTip: Supplier<String>) {
    this.icon = icon
    this.toolTip = toolTip
  }

  @Contract(pure = true)
  protected constructor(another: IconWrapperWithToolTip) {
    icon = another.icon
    toolTip = another.toolTip
  }

  override fun replaceBy(replacer: IconReplacer): IconWrapperWithToolTip {
    return IconWrapperWithToolTip(icon = replacer.replaceIcon(icon), toolTip = toolTip)
  }

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    icon.paintIcon(c, g, x, y)
  }

  override fun getIconWidth(): Int = icon.iconWidth

  override fun getIconHeight(): Int = icon.iconHeight

  override fun getToolTip(composite: Boolean): String? = toolTip.get()

  override fun hashCode(): Int = icon.hashCode()

  override fun equals(`object`: Any?): Boolean {
    return `object` === this || `object` is IconWrapperWithToolTip && `object`.icon == icon
  }

  override fun toString(): String = "IconWrapperWithTooltip:$icon"

  override fun copy(): Icon = IconWrapperWithToolTip(icon, toolTip)

  override fun retrieveIcon(): Icon = icon
}
