// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.outputs.NotebookOutputDataKey
import com.intellij.notebooks.visualization.outputs.NotebookOutputDataKeyExtractor
import com.intellij.notebooks.visualization.settings.NotebookSettings
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import java.util.concurrent.atomic.AtomicBoolean

class EditorCellOutputs(private val cell: EditorCell) {
  private val editor: EditorEx = cell.editor
  private val outputsLoaded = AtomicBoolean(false)

  val scrollingEnabled: AtomicBooleanProperty = AtomicBooleanProperty(
    NotebookSettings.getInstance().outputScrollingEnabledByDefault
  )
  val outputs: AtomicProperty<List<EditorCellOutput>> = AtomicProperty(emptyList())

  fun ensureOutputsLoaded() {
    if (outputsLoaded.compareAndSet(false, true)) {
      outputs.set(getOutputs())
    }
  }

  fun updateWith(keys: List<NotebookOutputDataKey>) {
    outputsLoaded.set(true)
    outputs.set(keys.map { EditorCellOutput(it) })
  }

  fun updateOutputs() {
    outputsLoaded.set(true)
    val outputDataKeys = getOutputs()
    updateOutputs(outputDataKeys)
  }

  private fun updateOutputs(newOutputs: List<EditorCellOutput>) = runInEdt {
    outputs.set(newOutputs)
  }

  private fun getOutputs(): List<EditorCellOutput> {
    // The editor may be detached (no project/virtualFile) while a queued scroll/layout event still
    // reaches updateIfInVisibleRect on EDT; bail out instead of dereferencing with `!!` (IJPL-247967).
    val project = editor.project ?: return emptyList()
    val virtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return emptyList()
    return NotebookOutputDataKeyExtractor.EP_NAME.extensionList
      .firstNotNullOfOrNull { it.extract(project, virtualFile, cell.interval) }
      ?.takeIf { it.isNotEmpty() }
      ?.map { EditorCellOutput(it) }
    ?: emptyList()
  }
}