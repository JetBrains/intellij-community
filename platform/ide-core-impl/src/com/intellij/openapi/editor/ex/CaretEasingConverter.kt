// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.EditorSettings
import com.intellij.util.xmlb.Converter

private const val LEGACY_SNAPPY = "NINJA"
private const val LEGACY_GLIDING = "EASE"

internal class CaretEasingConverter : Converter<EditorSettings.CaretEasing>() {
  override fun fromString(value: String): EditorSettings.CaretEasing = when (value) {
    LEGACY_SNAPPY -> EditorSettings.CaretEasing.SNAPPY
    LEGACY_GLIDING -> EditorSettings.CaretEasing.GLIDING
    else -> EditorSettings.CaretEasing.entries.firstOrNull { it.name == value } ?: EditorSettings.CaretEasing.SNAPPY
  }

  override fun toString(value: EditorSettings.CaretEasing): String = value.name
}
