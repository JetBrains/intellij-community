package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.NotebookCellInlayController
import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.NotebookCellLines.CellType
import com.intellij.notebooks.visualization.NotebookCellLines.Interval
import com.intellij.notebooks.visualization.NotebookIntervalPointer
import com.intellij.notebooks.visualization.UpdateContext
import com.intellij.notebooks.visualization.outputs.NotebookOutputDataKey
import com.intellij.notebooks.visualization.outputs.NotebookOutputDataKeyExtractor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.util.*
import java.awt.Dimension
import java.time.ZonedDateTime
import kotlin.reflect.KClass

private val CELL_EXTENSION_CONTAINER_KEY = Key<MutableMap<KClass<*>, EditorCellExtension>>("CELL_EXTENSION_CONTAINER_KEY")

class EditorCell(
  val notebook: EditorNotebook,
  var intervalPointer: NotebookIntervalPointer,
  val editor: EditorImpl,
) : Disposable, UserDataHolder by UserDataHolderBase() {

  val source: AtomicProperty<String> = AtomicProperty(getSource())

  val type: CellType = interval.type

  val interval: Interval get() = intervalPointer.get() ?: error("Invalid interval")
  val intervalOrNull: Interval? get() = intervalPointer.get()

  val view: EditorCellView?
    get() = NotebookCellInlayManager.get(editor)!!.views[this]

  var visible: AtomicBooleanProperty = AtomicBooleanProperty(true)

  val selected: AtomicBooleanProperty = AtomicBooleanProperty(false)

  val gutterAction: AtomicProperty<AnAction?> = AtomicProperty(null)

  val executionStatus: AtomicProperty<ExecutionStatus> = AtomicProperty<ExecutionStatus>(ExecutionStatus())

  val outputs: EditorCellOutputs = EditorCellOutputs(editor, this)

  private fun getSource(): String {
    val document = editor.document
    if (interval.lines.first + 1 >= document.lineCount) return ""
    val startOffset = document.getLineStartOffset(interval.lines.first + 1)
    val endOffset = document.getLineEndOffset(interval.lines.last)
    if (startOffset >= endOffset) return ""  // possible for empty cells
    return document.getText(TextRange(startOffset, endOffset))
  }

  override fun dispose() {
    cleanupExtensions()
  }

  private fun cleanupExtensions() {
    CELL_EXTENSION_CONTAINER_KEY.get(this)?.values?.forEach {
      if (it is Disposable) {
        Disposer.dispose(it)
      }
    }
  }

  fun update() {
    editor.updateManager.update { ctx -> update(ctx) }
  }

  fun update(updateCtx: UpdateContext) {
    view?.update(updateCtx)
  }

  fun updateInput() {
    source.set(getSource())
  }

  fun onViewportChange() {
    view?.onViewportChanges()
  }

  fun setGutterAction(action: AnAction?) {
    gutterAction.set(action)
  }

  inline fun <reified T : NotebookCellInlayController> getController(): T? {
    val lazyFactory = getLazyFactory(T::class)
    if (lazyFactory != null) {
      createLazyControllers(lazyFactory)
    }
    return view?.getExtension<T>()
  }

  @PublishedApi
  internal fun createLazyControllers(factory: NotebookCellInlayController.LazyFactory) {
    factory.cellOrdinalsInCreationBlock.add(interval.ordinal)
    editor.updateManager.update { ctx ->
      update(ctx)
    }
    factory.cellOrdinalsInCreationBlock.remove(interval.ordinal)
  }

  @PublishedApi
  internal fun <T : NotebookCellInlayController> getLazyFactory(type: KClass<T>): NotebookCellInlayController.LazyFactory? {
    return NotebookCellInlayController.Factory.EP_NAME.extensionList
      .filterIsInstance<NotebookCellInlayController.LazyFactory>()
      .firstOrNull { it.getControllerClass() == type.java }
  }

  fun updateOutputs(): Unit = editor.updateManager.update {
    outputs.updateOutputs()
  }


  fun requestCaret() {
    view?.requestCaret()
  }

  inline fun <reified T : EditorCellExtension> getExtension(): T? {
    return getExtension(T::class)
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : EditorCellExtension> getExtension(cls: KClass<T>): T? {
    val extensions = CELL_EXTENSION_CONTAINER_KEY.get(this) ?: return null
    return extensions[cls] as? T
  }

  fun <T : EditorCellExtension> addExtension(cls: KClass<T>, extension: T) {
    var map = CELL_EXTENSION_CONTAINER_KEY.get(this)
    if (map == null) {
      map = mutableMapOf()
      CELL_EXTENSION_CONTAINER_KEY.set(this, map)
    }
    map[cls] = extension
  }

  fun onBeforeRemove() {
    forEachExtension { it.onBeforeRemove() }
  }

  private fun forEachExtension(action: (EditorCellExtension) -> Unit) {
    CELL_EXTENSION_CONTAINER_KEY.get(this)?.values?.forEach { action(it) }
  }

  /** Called only in RD mode with a ready list of NotebookOutputDataKey, to avoid reading data from JSON which is missing on the frontend. */
  fun updateOutputs(keys: List<NotebookOutputDataKey>): Unit = editor.updateManager.update {
    outputs.outputs.set(keys.map { EditorCellOutput(it) })
  }

  data class ExecutionStatus(
    val status: ProgressStatus? = null,
    val count: Int? = null,
    val startTime: ZonedDateTime? = null,
    val endTime: ZonedDateTime? = null,
  )
}

class EditorCellOutputs(private val editor: EditorEx, private val cell: EditorCell) {

  val scrollingEnabled: AtomicBooleanProperty = AtomicBooleanProperty(true)

  val outputs: AtomicProperty<List<EditorCellOutput>> = AtomicProperty(getOutputs())

  fun updateOutputs() {
    val outputDataKeys = getOutputs()
    updateOutputs(outputDataKeys)
  }

  private fun updateOutputs(newOutputs: List<EditorCellOutput>) = runInEdt {
    outputs.set(newOutputs)
  }

  private fun getOutputs(): List<EditorCellOutput> =
    NotebookOutputDataKeyExtractor.EP_NAME.extensionList.asSequence()
      .mapNotNull { it.extract(editor as EditorImpl, cell.interval) }
      .firstOrNull()
      ?.takeIf { it.isNotEmpty() }
      ?.map { EditorCellOutput(it) }
    ?: emptyList()

}

class EditorCellOutput(dataKey: NotebookOutputDataKey) {

  val dataKey: AtomicProperty<NotebookOutputDataKey> = AtomicProperty(dataKey)

  val size: AtomicProperty<EditorCellOutputSize> = AtomicProperty(EditorCellOutputSize())
}

data class EditorCellOutputSize(
  val size: Dimension? = null,
  val collapsed: Boolean = false,
  val maximized: Boolean = false,
  val resized: Boolean = false,
)