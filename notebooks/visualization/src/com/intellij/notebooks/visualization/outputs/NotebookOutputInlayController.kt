package com.intellij.notebooks.visualization.outputs

import com.intellij.notebooks.visualization.outputs.impl.SurroundingComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.util.asSafely
import javax.swing.JComponent

val EditorCustomElementRenderer.notebookInlayOutputComponent: JComponent?
  get() = asSafely<JComponent>()?.components?.firstOrNull()?.asSafely<SurroundingComponent>()

val EditorCustomElementRenderer.notebookCellOutputComponents: List<JComponent>?
  get() = notebookInlayOutputComponent?.components?.firstOrNull()?.asSafely<JComponent>()?.components?.map { it as JComponent }

@Service
class NotebookOutputComponentFactoryGetter : Disposable, Runnable {
  var list: List<NotebookOutputComponentFactory<*, *>> = emptyList()
    get() =
      field.ifEmpty {
        field = makeValidatedList()
        field
      }
    private set

  init {
    NotebookOutputComponentFactory.EP_NAME.addChangeListener(this, this)
  }

  override fun run() {
    list = emptyList()
  }

  override fun dispose(): Unit = Unit

  private fun makeValidatedList(): MutableList<NotebookOutputComponentFactory<*, *>> {
    val newList = mutableListOf<NotebookOutputComponentFactory<*, *>>()
    for (extension in NotebookOutputComponentFactory.EP_NAME.extensionList) {
      val collidingExtension = newList
        .firstOrNull {
          (
            it.componentClass.isAssignableFrom(extension.componentClass) ||
            extension.componentClass.isAssignableFrom(it.componentClass)
          ) &&
          (
            it.outputDataKeyClass.isAssignableFrom(extension.outputDataKeyClass) ||
            extension.outputDataKeyClass.isAssignableFrom(it.outputDataKeyClass)
          )
        }
      if (collidingExtension != null) {
        thisLogger().error("Can't register $extension: it clashes with $collidingExtension by using similar component and data key classes.")
      }
      else {
        newList += extension
      }
    }
    return newList
  }

  companion object {
    @JvmStatic
    val instance: NotebookOutputComponentFactoryGetter get() = service()
  }
}