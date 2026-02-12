// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import org.jetbrains.icons.design.Color
import org.jetbrains.icons.design.RGBA

fun Color.toAwtColor(): java.awt.Color {
  @Suppress("UseJBColor")
  when (this) {
    is RGBA -> return java.awt.Color(red, green, blue, alpha)
  }
}