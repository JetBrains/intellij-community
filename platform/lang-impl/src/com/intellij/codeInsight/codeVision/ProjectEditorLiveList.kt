package com.intellij.codeInsight.codeVision

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reactive.IViewableSet
import com.jetbrains.rd.util.reactive.ViewableSet

class ProjectEditorLiveList(val lifetime: Lifetime, val project: Project) : EditorFactoryListener {
  val editorList: IViewableSet<Editor>
    get() = myEditorSet

  private val myEditorSet = ViewableSet<Editor>()

  init {
    val editorFactory = EditorFactory.getInstance()
    editorFactory.addEditorFactoryListener(this, lifetime.createNestedDisposable())
    editorFactory.allEditors.forEach {
      if (editorFilter(it))
        myEditorSet.add(it)
    }
    lifetime.onTermination {
      myEditorSet.clear()
    }
  }

  private fun editorFilter(editor: Editor): Boolean {
    return editor.project === project
  }

  override fun editorCreated(event: EditorFactoryEvent) {
    if (editorFilter(event.editor))
      myEditorSet.add(event.editor)
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    myEditorSet.remove(event.editor)
  }
}