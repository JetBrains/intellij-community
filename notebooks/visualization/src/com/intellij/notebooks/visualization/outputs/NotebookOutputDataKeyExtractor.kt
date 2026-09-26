// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.outputs

import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface NotebookOutputDataKeyExtractor {
  /**
   * Seeks somewhere for some data to be represented below [interval].
   *
   * @return
   *  `null` if the factory can never extract any data from the [interval] of the [file].
   *  An empty list if the factory managed to extract some information, and it literally means there's nothing to be shown.
   *  A non-empty list if some data can be shown.
   */
  fun extract(project: Project, file: VirtualFile, interval: NotebookCellLines.Interval): List<NotebookOutputDataKey>?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<NotebookOutputDataKeyExtractor> =
      ExtensionPointName.create("org.jetbrains.plugins.notebooks.editor.outputs.notebookOutputDataKeyExtractor")

    fun extractOutput(project: Project, file: VirtualFile, interval: NotebookCellLines.Interval): List<NotebookOutputDataKey> {
      return EP_NAME.extensionsIfPointIsRegistered.firstNotNullOfOrNull { it.extract(project, file, interval) } ?: emptyList()
    }
  }
}