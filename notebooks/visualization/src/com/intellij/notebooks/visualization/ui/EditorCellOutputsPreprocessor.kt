// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Called in EditorCellOutputsView.updateData before processing outputs.
 * Allows modifying outputs before they will become components.
 * For example, we can merge multiple consequence text outputs into a single one or to remove meaningless outputs.
 */
interface EditorCellOutputsPreprocessor {
  fun processOutputs(outputs: List<EditorCellOutput>): List<EditorCellOutput>

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<EditorCellOutputsPreprocessor> =
      ExtensionPointName.create("org.jetbrains.plugins.notebooks.editor.outputs.editorCellOutputsPreprocessor")
  }
}