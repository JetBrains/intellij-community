// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
@file:JvmName("LiveIndicatorIcons")
package com.intellij.ui.icons

import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.IconManager.Companion.getInstance
import com.intellij.ui.LayeredIcon
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.Icon

fun withLiveIndicatorIcon(base: Icon): Icon {
  return if (isNewUI()) getInstance().withIconBadge(base, JBUI.CurrentTheme.IconBadge.SUCCESS) else getLiveIndicatorIcon(base)
}

fun getLiveIndicatorIcon(base: Icon): Icon = getLiveIndicatorIcon(base, active = true)

fun getLiveIndicatorIcon(base: Icon, active: Boolean): Icon {
  return getLiveIndicatorIcon(base, if (active) Color.GREEN else Color.RED)
}

fun getLiveIndicatorIcon(base: Icon, color: Color): Icon {
  return LayeredIcon.layeredIcon(arrayOf(base, IndicatorIcon(base, base.iconWidth, base.iconHeight, color)))
}