package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Key
import java.awt.Component

private val COMPONENTS_CONTAINER = Key<EditorEmbeddedComponentContainer>("COMPONENTS_CONTAINER")

internal class EditorEmbeddedComponentContainer(private val editor: EditorEx) : Disposable {

  init {
    editor.contentComponent.layout = EditorEmbeddedComponentLayoutManager(editor)
    editor.putUserData(COMPONENTS_CONTAINER, this)
  }

  fun add(component: Component, constraints: Any) {
    editor.contentComponent.add(component, constraints)
  }

  fun remove(component: Component) {
    editor.contentComponent.remove(component)
  }

  override fun dispose() {
    editor.putUserData(COMPONENTS_CONTAINER, null)
  }

}

internal val Editor.componentContainer
  get() = COMPONENTS_CONTAINER.get(this)