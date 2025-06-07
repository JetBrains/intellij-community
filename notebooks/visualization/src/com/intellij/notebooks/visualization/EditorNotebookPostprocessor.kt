// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization

import com.intellij.notebooks.visualization.ui.EditorNotebook

interface EditorNotebookPostprocessor {
  fun postprocess(editorNotebook: EditorNotebook)
}
