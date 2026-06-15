// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.design

import com.intellij.platform.icons.IconManager

interface Color {
    fun toHex(): String

    companion object {
      val Transparent: Color = sRGB(0f, 0f, 0f, 0f)
      val Black: Color = sRGB(0f, 0f, 0f, 1f)
      val White: Color = sRGB(1f, 1f, 1f, 1f)
    }
}

interface SRGBColor : Color {
    val red: Float
    val green: Float
    val blue: Float
    val alpha: Float
}

/** Creates sRGB color, where 1f, 1f, 1f, 1f is fully opaque white. */
fun sRGB(red: Float, green: Float, blue: Float, alpha: Float): SRGBColor =
    IconManager.units().sRGB(red, green, blue, alpha)

/** Creates sRGB color from the given hex string. #FFFFFFFF is fully opaque white, the alpha component is optional. */
fun sRGB(hex: String): SRGBColor = IconManager.units().sRGBHex(hex)
