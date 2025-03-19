// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IViewableSet
import com.jetbrains.rd.util.reactive.ViewableSet

internal class ProjectEditorLiveList(lifetime: Lifetime, private val project: Project) : EditorFactoryListener {
  val editorList: IViewableSet<Editor>
    get() = editorSet

  private val editorSet = ViewableSet<Editor>()

  init {
    val editorFactory = EditorFactory.getInstance()
    editorFactory.addEditorFactoryListener(this, lifetime.createNestedDisposable())
    editorFactory.editorList.forEach {
      if (editorFilter(it)) {
        editorSet.add(it)
      }
    }
    lifetime.onTermination {
      editorSet.clear()
    }
  }

  private fun editorFilter(editor: Editor): Boolean {
    return editor.project === project
  }

  override fun editorCreated(event: EditorFactoryEvent) {
    if (editorFilter(event.editor))
      editorSet.add(event.editor)
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    editorSet.remove(event.editor)
  }
}