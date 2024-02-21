package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.util.asSafely
import org.jetbrains.plugins.notebooks.visualization.NOTEBOOK_CELL_LINES_INTERVAL_DATA_KEY
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayController
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputInlayController
import javax.swing.JComponent

class EditorCell(
  private val editor: Editor,
  private val intervals: NotebookCellLines,
  internal var intervalPointer: NotebookIntervalPointer
) {

  private var _controllers: List<NotebookCellInlayController> = emptyList()
  val controllers: List<NotebookCellInlayController>
    get() = _controllers + (input.inputController?.let { listOf(it) } ?: emptyList())

  private val interval get() = intervalPointer.get() ?: error("Invalid interval")

  private var input: EditorCellInput = EditorCellInput(
    editor as EditorEx,
    { currentController: NotebookCellInlayController? ->
      getInputFactories().firstNotNullOfOrNull {
        failSafeCompute(it, editor, currentController?.let { listOf(it) }
                                    ?: emptyList(), intervals.intervals.listIterator(interval.ordinal))
      }
    }, intervalPointer)

  private var output: EditorCellOutput? = null

  init {
    update()
  }

  fun dispose() {
    controllers.forEach { controller ->
      disposeController(controller)
    }
    input.dispose()
    output?.dispose()
  }

  private fun disposeController(controller: NotebookCellInlayController) {
    val inlay = controller.inlay
    inlay.renderer.asSafely<JComponent>()?.let { DataManager.removeDataProvider(it) }
    Disposer.dispose(inlay)
  }

  fun update() {
    val otherFactories = NotebookCellInlayController.Factory.EP_NAME.extensionList
      .filter { it !is NotebookCellInlayController.InputFactory }

    val controllersToDispose = controllers.toMutableSet()
    _controllers = if (!editor.isDisposed) {
      otherFactories.mapNotNull { factory -> failSafeCompute(factory, editor, controllers, intervals.intervals.listIterator(interval.ordinal)) }
    }
    else {
      emptyList()
    }
    controllersToDispose.removeAll(controllers.toSet())
    controllersToDispose.forEach { disposeController(it) }
    for (controller in controllers) {
      val inlay = controller.inlay
      inlay.renderer.asSafely<JComponent>()?.let { component ->
        val oldProvider = DataManager.getDataProvider(component)
        if (oldProvider != null && oldProvider !is NotebookCellDataProvider) {
          LOG.error("Overwriting an existing CLIENT_PROPERTY_DATA_PROVIDER. Old provider: $oldProvider")
        }
        DataManager.removeDataProvider(component)
        DataManager.registerDataProvider(component, NotebookCellDataProvider(editor, component) { interval })
      }
    }
    input.update()
    output?.dispose()
    val outputController = controllers.filterIsInstance<NotebookOutputInlayController>().firstOrNull()
    if (outputController != null) {
      output = EditorCellOutput(editor as EditorEx, outputController)
    }
  }

  private fun getInputFactories(): Sequence<NotebookCellInlayController.Factory> {
    return NotebookCellInlayController.Factory.EP_NAME.extensionList.asSequence()
      .filter { it is NotebookCellInlayController.InputFactory }
  }

  private fun failSafeCompute(factory: NotebookCellInlayController.Factory,
                              editor: Editor,
                              controllers: Collection<NotebookCellInlayController>,
                              intervalIterator: ListIterator<NotebookCellLines.Interval>): NotebookCellInlayController? {
    try {
      return factory.compute(editor as EditorImpl, controllers, intervalIterator)
    }
    catch (t: Throwable) {
      thisLogger().error("${factory.javaClass.name} shouldn't throw exceptions at NotebookCellInlayController.Factory.compute(...)", t)
      return null
    }
  }

  fun updatePositions() {
    input.updatePositions()
    output?.updatePositions()
  }

  companion object {
    private val LOG = logger<EditorCell>()
  }

  private data class NotebookCellDataProvider(
    val editor: Editor,
    val component: JComponent,
    val intervalProvider: () -> NotebookCellLines.Interval,
  ) : DataProvider {
    override fun getData(key: String): Any? =
      when (key) {
        NOTEBOOK_CELL_LINES_INTERVAL_DATA_KEY.name -> intervalProvider()
        PlatformCoreDataKeys.CONTEXT_COMPONENT.name -> component
        PlatformDataKeys.EDITOR.name -> editor
        else -> null
      }
  }

}