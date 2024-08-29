/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName

interface InlayOutputProvider {
  fun acceptType(type: String): Boolean
  /**
   * If true, then this output can have a huge height, and IDE can limit it by adding scrollbar.
   * In other case scrollbar should never be displayed
   */
  fun shouldLimitHeight() = true

  fun create(parent: Disposable, editor: Editor): InlayOutput

  companion object {
    val EP = ExtensionPointName.create<InlayOutputProvider>("com.intellij.datavis.inlays.components.inlayOutputProvider")
  }
}
