// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.design

import com.intellij.platform.icons.IconManager

sealed interface Shape {
    companion object {}
}

interface Circle : Shape {
    val radius: IconUnit
}

interface Rectangle : Shape {
    val width: IconUnit
    val height: IconUnit
}

fun circle(radius: IconUnit): Circle = IconManager.units().circle(radius)

fun rectangle(width: IconUnit, height: IconUnit): Rectangle = IconManager.units().rectangle(width, height)
