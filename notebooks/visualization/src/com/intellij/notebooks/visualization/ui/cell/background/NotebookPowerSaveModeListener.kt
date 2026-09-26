// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.background

import com.intellij.ide.PowerSaveMode
import com.intellij.notebooks.visualization.ui.notebook
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project

internal class NotebookPowerSaveModeListener(val project: Project) : PowerSaveMode.Listener {
  override fun powerSaveStateChanged() {
    EditorFactory.getInstance().allEditors.forEach { editor ->
      editor.notebook?.cells?.forEach { it.checkAndRebuildInlays() }
    }
  }
}