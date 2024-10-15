package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.NotebookCellInlayController
import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.NotebookIntervalPointer
import com.intellij.notebooks.visualization.UpdateContext
import com.intellij.notebooks.visualization.execution.ExecutionEvent
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
import java.time.ZonedDateTime
import kotlin.reflect.KClass

private val CELL_EXTENSION_CONTAINER_KEY = Key<MutableMap<KClass<*>, EditorCellExtension>>("CELL_EXTENSION_CONTAINER_KEY")

class EditorCell(
  private val editor: EditorEx,
  val manager: NotebookCellInlayManager,
  var intervalPointer: NotebookIntervalPointer,
) : Disposable, UserDataHolder by UserDataHolderBase() {

  val source = AtomicProperty<String>(getSource())

  val type = interval.type

  val interval get() = intervalPointer.get() ?: error("Invalid interval")

  val view: EditorCellView?
    get() = manager.views[this]

  var visible = AtomicBooleanProperty(true)

  val selected = AtomicBooleanProperty(false)

  val gutterAction = AtomicProperty<AnAction?>(null)

  val executionStatus = AtomicProperty<ExecutionStatus>(ExecutionStatus())

  val outputs = AtomicProperty<List<NotebookOutputDataKey>>(getOutputs())

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
    manager.update { ctx -> update(ctx) }
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
    manager.update { ctx ->
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

  fun updateOutputs() {
    val outputDataKeys = getOutputs()
    updateOutputs(outputDataKeys)
  }

  private fun getOutputs(): List<NotebookOutputDataKey> =
    NotebookOutputDataKeyExtractor.EP_NAME.extensionList.asSequence()
      .mapNotNull { it.extract(editor as EditorImpl, interval) }
      .firstOrNull()
      ?.takeIf { it.isNotEmpty() }
    ?: emptyList()

  private fun updateOutputs(newOutputs: List<NotebookOutputDataKey>) = runInEdt {
    outputs.set(newOutputs)
  }

  fun onExecutionEvent(event: ExecutionEvent) {
    when (event) {
      is ExecutionEvent.ExecutionStarted -> {
        executionStatus.set(executionStatus.get().copy(status = event.status, startTime = event.startTime))
      }
      is ExecutionEvent.ExecutionStopped -> {
        executionStatus.set(executionStatus.get().copy(status = event.status, endTime = event.endTime, count = event.executionCount))
      }
      is ExecutionEvent.ExecutionSubmitted -> {
        executionStatus.set(executionStatus.get().copy(status = event.status))
      }
      is ExecutionEvent.ExecutionReset -> {
        executionStatus.set(executionStatus.get().copy(status = event.status))
      }
    }
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
      map = mutableMapOf<KClass<*>, EditorCellExtension>()
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

  data class ExecutionStatus(
    val status: ProgressStatus? = null,
    val count: Int? = null,
    val startTime: ZonedDateTime? = null,
    val endTime: ZonedDateTime? = null,
  )
}