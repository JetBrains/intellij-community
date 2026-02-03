// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.vcs.ex.Range
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

/**
 * Supplies colors to various parts of line status marking machinery
 */
@ApiStatus.Experimental
open class LineStatusMarkerColorScheme {
  /**
   * Main change color
   * Primarily used to paint filled gutter marker
   */
  open fun getColor(editor: Editor, type: Byte): Color? {
    val scheme = editor.getColorsScheme()
    return when (type) {
      Range.INSERTED -> scheme.getColor(EditorColors.ADDED_LINES_COLOR)
      Range.DELETED -> scheme.getColor(EditorColors.DELETED_LINES_COLOR)
      Range.MODIFIED -> scheme.getColor(EditorColors.MODIFIED_LINES_COLOR)
      Range.EQUAL -> scheme.getColor(EditorColors.WHITESPACES_MODIFIED_LINES_COLOR)
      else -> error("Invalid change type")
    }
  }

  /**
   * Color for the border around the change
   */
  open fun getBorderColor(editor: Editor): Color? {
    return editor.getColorsScheme().getColor(EditorColors.BORDER_LINES_COLOR)
  }

  /**
   * Main change color for editor error stripe
   */
  open fun getErrorStripeColor(type: Byte): Color? {
    val scheme = EditorColorsManager.getInstance().getGlobalScheme()
    return when (type) {
      Range.INSERTED -> scheme.getAttributes(DiffColors.DIFF_INSERTED)
      Range.DELETED -> scheme.getAttributes(DiffColors.DIFF_DELETED)
      Range.MODIFIED -> scheme.getAttributes(DiffColors.DIFF_MODIFIED)
      else -> error("Invalid change type")
    }.errorStripeColor
  }

  /**
   * Color of the ignored change
   * Primarily used to paint non-filled gutter marker
   */
  open fun getIgnoredBorderColor(editor: Editor, type: Byte): Color? {
    val scheme = editor.getColorsScheme()
    return when (type) {
      Range.INSERTED -> scheme.getColor(EditorColors.IGNORED_ADDED_LINES_BORDER_COLOR)
      Range.DELETED -> scheme.getColor(EditorColors.IGNORED_DELETED_LINES_BORDER_COLOR)
      Range.MODIFIED, Range.EQUAL -> scheme.getColor(EditorColors.IGNORED_MODIFIED_LINES_BORDER_COLOR)
      else -> error("Invalid change type")
    }
  }

  companion object {
    @JvmField
    val DEFAULT = LineStatusMarkerColorScheme()
  }
}