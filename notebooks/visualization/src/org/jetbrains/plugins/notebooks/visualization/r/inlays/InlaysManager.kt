/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Key

/**
 * Manages inlays.
 *
 * On project load subscribes
 *    on editor opening/closing.
 *    on adding/removing notebook cells
 *    on any document changes
 *    on folding actions
 *
 * On editor open checks the PSI structure and restores saved inlays.
 *
 * ToDo should be split into InlaysManager with all basics and NotebookInlaysManager with all specific.
 */

private val logger = Logger.getInstance(InlaysManager::class.java)

class InlaysManager : EditorFactoryListener {

  companion object {
    private val KEY = Key.create<EditorInlaysManager>("org.jetbrains.plugins.notebooks.visualization.r.inlays.editorInlaysManager")

    fun getEditorManager(editor: Editor): EditorInlaysManager? = editor.getUserData(KEY)

    private fun getDescriptor(editor: Editor): InlayElementDescriptor? {
      return InlayDescriptorProvider.EP.extensionList
        .asSequence()
        .mapNotNull { it.getInlayDescriptor(editor) }
        .firstOrNull()
    }
  }

  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    val project = editor.project ?: return
    val descriptor = getDescriptor(editor) ?: return
    InlayDimensions.init(editor as EditorImpl)
    editor.putUserData(KEY, EditorInlaysManager(project, editor, descriptor))
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    event.editor.getUserData(KEY)?.let { manager ->
      manager.dispose()
      event.editor.putUserData(KEY, null)
    }
  }
}