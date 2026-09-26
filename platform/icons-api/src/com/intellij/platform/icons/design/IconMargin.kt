// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.design

import com.intellij.platform.icons.IconManager

interface IconMargin {
    val top: IconUnit
    val left: IconUnit
    val bottom: IconUnit
    val right: IconUnit

    companion object {
        val Zero: IconMargin = iconMargin(0.dp, 0.dp, 0.dp, 0.dp)
    }
}

fun iconMargin(top: IconUnit, left: IconUnit, bottom: IconUnit, right: IconUnit): IconMargin =
    IconManager.units().margin(top, left, bottom, right)
