// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


@Service
class VisualFormattingLayerFacade(val project: Project) {

  val newEditorListener = VisualFormattingLayerNewEditorListener(project)
  val lock = ReentrantLock()
  val editorFactory: EditorFactory by lazy { EditorFactory.getInstance() }

  var enabled = false
    private set

  fun enable() {
    lock.withLock {
      if (!enabled) {
        editorFactory.addEditorFactoryListener(newEditorListener)
        editorFactory
          .allEditors
          .filter { it.project == project }
          .forEach { it.addVisualLayer() }
        enabled = true
      }
    }
  }

  fun disable() {
    lock.withLock {
      if (enabled) {
        editorFactory.removeEditorFactoryListener(newEditorListener)
        editorFactory
          .allEditors
          .filter { it.project == project }
          .forEach { it.removeVisualLayer() }
        enabled = false
      }
    }
  }

}
