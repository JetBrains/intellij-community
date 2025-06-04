// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.controllers.selfUpdate

import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.notebooks.visualization.ui.EditorCellView
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer

interface SelfManagedControllerFactory {
  fun createController(editorCell: EditorCell): SelfManagedCellController?

  companion object {
    private val EP: ExtensionPointName<SelfManagedControllerFactory> = ExtensionPointName.create<SelfManagedControllerFactory>("org.jetbrains.plugins.notebooks.notebookCellSelfManagedController")

    fun createControllers(cellView: EditorCellView): List<SelfManagedCellController> {
      return createExternalControllers(cellView)
    }

    private fun createExternalControllers(cellView: EditorCellView): List<SelfManagedCellController> = EP.extensionList.mapNotNull {
      it.createController(cellView.cell)?.also {
        Disposer.register(cellView, it)
      }
    }
  }
}