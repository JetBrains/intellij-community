// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColorWrapper
import com.intellij.util.ui.ComparableColor
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.util.*

@ApiStatus.Internal
class TextAttributeKeyColor(color: Color, val keyName: String, val type: TextAttributeKeyColorType) : ColorWrapper(color) {
  override fun getPresentableName(): @NlsSafe String {
    return "TextAttributeKey: $keyName"
  }

  override fun colorEquals(other: ComparableColor): Boolean {
    return other is TextAttributeKeyColor && keyName == other.keyName && type == other.type &&
           this == other
  }

  override fun colorHashCode(): Int {
    return Objects.hash(keyName, type)
  }
}

@ApiStatus.Internal
enum class TextAttributeKeyColorType { FOREGROUND, BACKGROUND, ERROR_STRIPE, EFFECT_COLOR }
