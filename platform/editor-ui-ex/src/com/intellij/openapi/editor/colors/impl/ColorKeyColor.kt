// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.ComparableColor
import com.intellij.util.ui.PresentableColor
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@ApiStatus.Internal
class ColorKeyColor(color: Color, val keyName: String) : Color(color.rgb, true), PresentableColor, ComparableColor {
  override fun getPresentableName(): @NlsSafe String? {
    return "ColorKey: $keyName"
  }

  override fun colorEquals(other: ComparableColor): Boolean {
    return other is ColorKeyColor && keyName == other.keyName &&
           this == other
  }

  override fun colorHashCode(): Int {
    return keyName.hashCode()
  }
}