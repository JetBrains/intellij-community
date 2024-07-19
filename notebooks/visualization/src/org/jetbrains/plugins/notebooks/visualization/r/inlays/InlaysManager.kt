/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorImpl

/**
 * Obsolete code, currently EditorInlaysManager is not created
 */
class InlaysManager : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    editor.project ?: return
    /**
     * may be [InlayDimensions.init] is not needed, this call was missed for a long time
     */
    InlayDimensions.init(editor as EditorImpl)
  }
}
