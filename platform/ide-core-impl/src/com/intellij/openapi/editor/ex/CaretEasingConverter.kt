// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.EditorSettings
import com.intellij.util.xmlb.Converter

private const val PERSISTED_SNAPPY = "NINJA"
private const val PERSISTED_GLIDING = "EASE"

/**
 * Saves the caret easing with the names of the 2026.2 constants.
 *
 * The 2026.2 enum has only the constants `NINJA` and `EASE`, and 2026.2 has no converter for it.
 * xmlb reads an unknown name, such as `GLIDING`, as `null`.
 * This `null` stops a 2026.2 build from creating any editor.
 * Backup and Sync can bring a newer `editor.xml` to a 2026.2 build, because it shares one snapshot for all versions of a product.
 * Thus, keep the 2026.2 names in the saved file, although the constants are now `SNAPPY` and `GLIDING`.
 *
 * Reads both the 2026.2 names and the current constant names, because some 2026.3 EAP builds saved the current names.
 * See IJPL-257317.
 */
internal class CaretEasingConverter : Converter<EditorSettings.CaretEasing>() {
  override fun fromString(value: String): EditorSettings.CaretEasing = when (value) {
    PERSISTED_SNAPPY -> EditorSettings.CaretEasing.SNAPPY
    PERSISTED_GLIDING -> EditorSettings.CaretEasing.GLIDING
    else -> EditorSettings.CaretEasing.entries.firstOrNull { it.name == value } ?: EditorSettings.CaretEasing.SNAPPY
  }

  override fun toString(value: EditorSettings.CaretEasing): String = when (value) {
    EditorSettings.CaretEasing.SNAPPY -> PERSISTED_SNAPPY
    EditorSettings.CaretEasing.GLIDING -> PERSISTED_GLIDING
  }
}
