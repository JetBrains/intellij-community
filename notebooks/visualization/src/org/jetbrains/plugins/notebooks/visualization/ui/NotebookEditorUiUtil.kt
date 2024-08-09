package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager.Properties.RendererFactory
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

fun EditorEx.addComponentInlay(
  component: JComponent,
  isRelatedToPrecedingText: Boolean,
  showAbove: Boolean,
  showWhenFolded: Boolean = true,
  priority: Int,
  offset: Int,
  rendererFactory: RendererFactory? = null
): Inlay<*> {
  // see DS-5614
  val fullWidthArg: Boolean = this.editorKind != EditorKind.DIFF
  val inlay = EditorEmbeddedComponentManager.getInstance().addComponent(
    this,
    component,
    EditorEmbeddedComponentManager.Properties(
      EditorEmbeddedComponentManager.ResizePolicy.none(),
      rendererFactory,
      isRelatedToPrecedingText,
      showAbove,
      showWhenFolded,
      fullWidthArg,
      priority,
      offset,
    )
  )
  if (inlay == null) {
    if (isDisposed) {
      throw IllegalStateException("Editor is disposed")
    }
    throw IllegalStateException(
      "Component is null for $component, $isRelatedToPrecedingText, $showAbove, $showWhenFolded, $priority, $offset")
  }

  updateUiOnParentResizeImpl(component.parent as JComponent, WeakReference(component))
  component.revalidate()
  return inlay
}

private fun updateUiOnParentResizeImpl(parent: JComponent, childRef: WeakReference<JComponent>) {
  val listener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      WriteIntentReadAction.run {
        val child = childRef.get()
        if (child != null) {
          child.updateUI()
        }
        else {
          parent.removeComponentListener(this)
        }
      }
    }
  }
  parent.addComponentListener(listener)
}

/**
 * Seeks for an [EditorComponentImpl] in the component hierarchy, calls [updateHandler] initially and every time
 * the [component] is detached or attached to some component with the actual editor.
 */
fun registerEditorSizeWatcher(
  component: JComponent,
  updateHandler: () -> Unit,
) {
  var editorComponent: EditorComponentImpl? = null
  var scrollComponent: JScrollPane? = null

  updateHandler()

  // Holds strong reference to the editor. Incautious usage may cause editor leakage.
  val editorResizeListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent): Unit = updateHandler()
  }

  component.addHierarchyListener { event ->
    if (event.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() != 0L) {
      val newEditor: EditorComponentImpl? = generateSequence(component.parent) { it.parent }
        .filterIsInstance<EditorComponentImpl>()
        .firstOrNull()
      if (editorComponent !== newEditor) {
        (scrollComponent ?: editorComponent)?.removeComponentListener(editorResizeListener)
        editorComponent = newEditor
        // if editor is located inside a scroll pane, we should listen to its size instead of editor component
        scrollComponent = generateSequence(editorComponent?.parent) { it.parent }
          .filterIsInstance<JScrollPane>()
          .firstOrNull()
        (scrollComponent ?: editorComponent)?.addComponentListener(editorResizeListener)
        updateHandler()
      }
    }
  }
}

val EditorEx.textEditingAreaWidth: Int
  get() = scrollingModel.visibleArea.width - scrollPane.verticalScrollBar.width

fun JComponent.yOffsetFromEditor(editor: Editor): Int? =
  SwingUtilities.convertPoint(this, 0, 0, editor.contentComponent).y
    .takeIf { it >= 0 }
    ?.let { it + insets.top }
