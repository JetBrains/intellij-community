// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.EditorCellInputFactory

class TextEditorCellInputFactory : EditorCellInputFactory {
  override fun createComponent(cell: EditorCell): TextEditorCellViewComponent = TextEditorCellViewComponent(cell)
  override fun supports(cell: EditorCell): Boolean = true
}