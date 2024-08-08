package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Key
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

private val COMPONENTS_CONTAINER = Key<EditorEmbeddedComponentContainer>("COMPONENTS_CONTAINER")

internal class EditorEmbeddedComponentContainer(private val editor: EditorEx): JComponent(), Disposable {

  private val contentResizeListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      invalidate()
    }
  }

  init {
    editor.contentComponent.add(this)
    editor.contentComponent.addComponentListener(contentResizeListener)
    editor.putUserData(COMPONENTS_CONTAINER, this)
    layout = EditorEmbeddedComponentLayoutManager(editor.scrollPane)
    putClientProperty(SelectClickedCellEventHelper.SKIP_CLICK_PROCESSING_FOR_CELL_SELECTION, true)
  }

  override fun doLayout() {
    bounds = SwingUtilities.calculateInnerArea(editor.contentComponent, null)
    super.doLayout()
  }

  override fun dispose() {
    editor.contentComponent.removeComponentListener(contentResizeListener)
    editor.contentComponent.remove(this)
    editor.putUserData(COMPONENTS_CONTAINER, null)
  }

}

internal val Editor.componentContainer
  get() = COMPONENTS_CONTAINER.get(this)