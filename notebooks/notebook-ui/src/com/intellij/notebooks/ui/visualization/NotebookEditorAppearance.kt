// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.util.Key


/**
 * Constants and functions that affects only visual representation, like colors, sizes of elements, etc.
 */
interface NotebookEditorAppearance : NotebookEditorAppearanceColors, NotebookEditorAppearanceSizes, NotebookEditorAppearanceFlags {
  companion object {
    val NOTEBOOK_APPEARANCE_KEY: Key<NotebookEditorAppearance?> = Key.create<NotebookEditorAppearance>(NotebookEditorAppearance::class.java.name)
    val CODE_CELL_BACKGROUND: ColorKey = ColorKey.createColorKey("JUPYTER.CODE_CELL_BACKGROUND")
    val EDITOR_BACKGROUND: ColorKey = ColorKey.createColorKey("JUPYTER.EDITOR_BACKGROUND")
    internal val CELL_STRIPE_HOVERED_COLOR_OLD: ColorKey = ColorKey.createColorKey("JUPYTER.CELL_UNDER_CURSOR_STRIPE_HOVER_COLOR")
    internal val CELL_STRIPE_SELECTED_COLOR_OLD: ColorKey = ColorKey.createColorKey("JUPYTER.CELL_UNDER_CARET_COMMAND_MODE_STRIPE_COLOR")
    internal val CELL_TOOLBAR_BORDER_COLOR_OLD: ColorKey = ColorKey.createColorKey("JUPYTER.SAUSAGE_BUTTON_BORDER_COLOR")
    val CARET_ROW_COLOR: ColorKey = ColorKey.createColorKey("JUPYTER.CARET_ROW_COLOR")
    val CELL_STRIPE_HOVERED_COLOR: ColorKey = ColorKey.createColorKey("JUPYTER.CELL_STRIPE_HOVERED_COLOR")
    val CELL_STRIPE_SELECTED_COLOR: ColorKey = ColorKey.createColorKey("JUPYTER.CELL_STRIPE_SELECTED_COLOR")
    val CELL_FRAME_SELECTED_COLOR: ColorKey = ColorKey.createColorKey("JUPYTER.CELL_FRAME_SELECTED_COLOR")
    val CELL_FRAME_HOVERED_COLOR: ColorKey = ColorKey.createColorKey("JUPYTER.CELL_FRAME_BORDER_COLOR")
    val CELL_TOOLBAR_BORDER_COLOR: ColorKey = ColorKey.createColorKey("JUPYTER.CELL_TOOLBAR_BORDER_COLOR")
  }
}