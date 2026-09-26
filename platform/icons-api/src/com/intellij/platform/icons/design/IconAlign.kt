// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.design

import com.intellij.platform.icons.IconManager

@Suppress("unused")
interface IconAlign {
    val verticalAlign: IconVerticalAlign
    val horizontalAlign: IconHorizontalAlign

    companion object {
        val TopLeft: IconAlign = iconAlign(IconVerticalAlign.Top, IconHorizontalAlign.Left)
        val TopCenter: IconAlign = iconAlign(IconVerticalAlign.Top, IconHorizontalAlign.Center)
        val TopRight: IconAlign = iconAlign(IconVerticalAlign.Top, IconHorizontalAlign.Right)
        val CenterLeft: IconAlign = iconAlign(IconVerticalAlign.Center, IconHorizontalAlign.Left)
        val Center: IconAlign = iconAlign(IconVerticalAlign.Center, IconHorizontalAlign.Center)
        val CenterRight: IconAlign = iconAlign(IconVerticalAlign.Center, IconHorizontalAlign.Right)
        val BottomLeft: IconAlign = iconAlign(IconVerticalAlign.Bottom, IconHorizontalAlign.Left)
        val BottomCenter: IconAlign = iconAlign(IconVerticalAlign.Bottom, IconHorizontalAlign.Center)
        val BottomRight: IconAlign = iconAlign(IconVerticalAlign.Bottom, IconHorizontalAlign.Right)
    }
}

fun iconAlign(verticalAlign: IconVerticalAlign, horizontalAlign: IconHorizontalAlign): IconAlign =
    IconManager.units().align(verticalAlign, horizontalAlign)

enum class IconVerticalAlign {
    Top,
    Center,
    Bottom,
}

enum class IconHorizontalAlign {
    Left,
    Center,
    Right,
}
