package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.*
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.visualization.NotebookCellInlayController
import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.NotebookIntervalPointer
import com.intellij.notebooks.visualization.UpdateContext
import com.intellij.notebooks.visualization.execution.ExecutionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.observable.properties.AtomicProperty
import java.time.ZonedDateTime
import kotlin.reflect.KClass

private val CELL_EXTENSION_CONTAINER_KEY = Key<MutableMap<KClass<*>, EditorCellExtension>>("CELL_EXTENSION_CONTAINER_KEY")

class EditorCell(
  private val editor: EditorEx,
  val manager: NotebookCellInlayManager,
  var intervalPointer: NotebookIntervalPointer,
  parentScope: CoroutineScope,
  private val viewFactory: (EditorCell) -> EditorCellView,
) : Disposable, UserDataHolder by UserDataHolderBase() {

  private val coroutineScope = parentScope.childScope("EditorCell")

  private val _source = MutableStateFlow<String>(getSource())
  val source = _source.asStateFlow()

  private fun getSource(): String {
    val document = editor.document
    if (interval.lines.first + 1 >= document.lineCount) return ""
    val startOffset = document.getLineStartOffset(interval.lines.first + 1)
    val endOffset = document.getLineEndOffset(interval.lines.last)
    if (startOffset >= endOffset) return ""  // possible for empty cells
    return document.getText(TextRange(startOffset, endOffset))
  }

  val type = interval.type

  val interval get() = intervalPointer.get() ?: error("Invalid interval")

  var view: EditorCellView? = null

  var visible: Boolean = true
    set(value) {
      if (field == value) return
      field = value
      manager.update<Unit> { ctx ->
        if (!value) {
          view?.let {
            disposeView(it)
          }
        }
        else {
          if (view == null) {
            view = createView()
          }
        }
      }
    }

  init {
    CELL_EXTENSION_CONTAINER_KEY.set(this, mutableMapOf())
  }

  fun initView() {
    view = createView()
  }

  private fun createView(): EditorCellView = manager.update { ctx ->
    val view = viewFactory(this).also { Disposer.register(this, it) }
    gutterAction?.let { view.setGutterAction(it) }
    view.updateExecutionStatus(executionCount, progressStatus, executionStartTime, executionEndTime)
    view.selected = selected
    manager.fireCellViewCreated(view)
    view.updateCellFolding(ctx)
    view
  }

  private fun disposeView(it: EditorCellView) {
    Disposer.dispose(it)
    view = null
    manager.fireCellViewRemoved(it)
  }

  var selected: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        view?.selected = value
      }
    }

  var gutterAction: AnAction? = null
    private set

  private var executionCount: Int? = null

  private var progressStatus: ProgressStatus? = null

  private var executionStartTime: ZonedDateTime? = null

  private var executionEndTime: ZonedDateTime? = null

  val mode = AtomicProperty<NotebookEditorMode>(NotebookEditorMode.COMMAND)

  override fun dispose() {
    cleanupExtensions()
    view?.let { disposeView(it) }
    coroutineScope.cancel()
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
    coroutineScope.launch(Dispatchers.Main) {
      _source.emit(getSource())
    }
    view?.updateInput()
  }

  fun onViewportChange() {
    view?.onViewportChanges()
  }

  fun setGutterAction(action: AnAction?) {
    gutterAction = action
    view?.setGutterAction(action)
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
    view?.updateOutputs()
  }

  fun onExecutionEvent(event: ExecutionEvent) {
    when (event) {
      is ExecutionEvent.ExecutionStarted -> {
        executionStartTime = event.startTime
        progressStatus = event.status
      }
      is ExecutionEvent.ExecutionStopped -> {
        executionEndTime = event.endTime
        progressStatus = event.status
        executionCount = event.executionCount
      }
      is ExecutionEvent.ExecutionSubmitted -> {
        progressStatus = event.status
      }
      is ExecutionEvent.ExecutionReset -> {
        progressStatus = event.status
      }
    }
    view?.updateExecutionStatus(executionCount, progressStatus, executionStartTime, executionEndTime)
  }

  fun switchToEditMode() = runInEdt {
    mode.set(NotebookEditorMode.EDIT)
  }

  fun switchToCommandMode() = runInEdt {
    mode.set(NotebookEditorMode.COMMAND)
  }

  fun requestCaret() {
    view?.requestCaret()
  }

  inline fun <reified T : EditorCellExtension> getExtension(): T {
    return getExtension(T::class)
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : EditorCellExtension> getExtension(cls: KClass<T>): T {
    return CELL_EXTENSION_CONTAINER_KEY.get(this)!![cls] as T
  }

  fun <T : EditorCellExtension> addExtension(cls: KClass<T>, extension: T) {
    CELL_EXTENSION_CONTAINER_KEY.get(this)!![cls] = extension
  }

  fun onBeforeRemove() {
    forEachExtension { it.onBeforeRemove() }
  }

  private fun forEachExtension(action: (EditorCellExtension) -> Unit) {
    CELL_EXTENSION_CONTAINER_KEY.get(this)?.values?.forEach { action(it) }
  }
}