// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.design

import com.intellij.platform.icons.IconManager

/**
 * Samples:
 * <pre>
 *   20.px
 *  * 100.percent
 *  * 0.5.fraction
 *  * 5.dp
 *  * AutoIconUnit - fills max width
 * </pre>
 */
sealed interface IconUnit {
  operator fun times(other: Int): IconUnit
  operator fun times(other: Double): IconUnit

    companion object {
        val Zero: IconUnit = 0.dp
    }
}

interface DisplayPoint : IconUnit {
    val value: Double

    operator fun plus(other: DisplayPoint): DisplayPoint
}

interface Pixel : IconUnit {
    val value: Int

    operator fun plus(other: Pixel): Pixel
}

val Int.dp: DisplayPoint
    get() = IconManager.units().dp(this.toDouble())

val Double.dp: DisplayPoint
    get() = IconManager.units().dp(this)

val Float.dp: DisplayPoint
    get() = IconManager.units().dp(this.toDouble())

val Int.px: Pixel
    get() = IconManager.units().px(this)
