// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.outputs.NotebookOutputDataKeyExtractor
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty

class EditorCellOutputs(private val cell: EditorCell) {
  private val editor: EditorEx = cell.editor
  val scrollingEnabled: AtomicBooleanProperty = AtomicBooleanProperty(true)
  val outputs: AtomicProperty<List<EditorCellOutput>> = AtomicProperty(getOutputs())

  fun updateOutputs() {
    val outputDataKeys = getOutputs()
    updateOutputs(outputDataKeys)
  }

  private fun updateOutputs(newOutputs: List<EditorCellOutput>) = runInEdt {
    outputs.set(newOutputs)
  }

  private fun getOutputs(): List<EditorCellOutput> =
    NotebookOutputDataKeyExtractor.Companion.EP_NAME.extensionList.asSequence()
      .mapNotNull { it.extract(editor as EditorImpl, cell.interval) }
      .firstOrNull()
      ?.takeIf { it.isNotEmpty() }
      ?.map { EditorCellOutput(it) }
    ?: emptyList()
}