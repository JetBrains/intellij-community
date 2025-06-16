// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.outputs.NotebookOutputDataKey
import com.intellij.openapi.observable.properties.AtomicProperty

class EditorCellOutput(dataKey: NotebookOutputDataKey) {
  val dataKey: AtomicProperty<NotebookOutputDataKey> = AtomicProperty(dataKey)
  val size: AtomicProperty<EditorCellOutputSize> = AtomicProperty(EditorCellOutputSize())
}