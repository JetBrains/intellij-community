package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.plaf.PanelUI

/**
 * A workaround base class for panels with custom UI. Every look and feel change in the IDE (like theme, font family and size, etc.)
 * leads to call of the `updateUI()`. All default Swing implementations resets the UI to the default one.
 */
open class SteadyUIPanel(private val steadyUi: PanelUI) : JPanel() {
  init {
    // Required for correct UI initialization. It leaks in the super class anyway.
    @Suppress("LeakingThis") setUI(steadyUi)
  }

  override fun updateUI() {
    // Update the UI. Don't call super.updateUI() to prevent resetting to the default UI.
    // There's another way to set specific UI for specific components: by defining java.swing.plaf.ComponentUI#createUI and overriding
    // java.swing.JComponent#getUIClassID. This approach can't be used in our code since it requires UI classes to have empty constructors,
    // while in reality some additional data should be provided to UI instances in advance.
    setUI(steadyUi)
  }
}

fun EditorEx.addComponentInlay(
  component: JComponent,
  isRelatedToPrecedingText: Boolean,
  showAbove: Boolean,
  showWhenFolded: Boolean = true,
  priority: Int,
  offset: Int,
): Inlay<*> {
  val inlay = EditorEmbeddedComponentManager.getInstance().addComponent(
    this,
    component,
    EditorEmbeddedComponentManager.Properties(
      EditorEmbeddedComponentManager.ResizePolicy.none(),
      null,
      isRelatedToPrecedingText,
      showAbove,
      showWhenFolded,
      priority,
      offset,
    )
  )!!

  updateUiOnParentResizeImpl(component.parent as JComponent, WeakReference(component))
  return inlay
}

private fun updateUiOnParentResizeImpl(parent: JComponent, childRef: WeakReference<JComponent>) {
  val listener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      val child = childRef.get()
      if (child != null) {
        child.updateUI()
      }
      else {
        parent.removeComponentListener(this)
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
