/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName

interface MultiOutputProvider {
  fun create(editor: Editor, parent: Disposable): NotebookInlayState

  companion object {
    val EP = ExtensionPointName.create<MultiOutputProvider>("com.intellij.datavis.inlays.components.multiOutputProvider")
  }
}