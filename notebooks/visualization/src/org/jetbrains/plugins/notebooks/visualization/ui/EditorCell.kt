package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayController
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayManager
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer
import org.jetbrains.plugins.notebooks.visualization.execution.ExecutionEvent
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.progress.ProgressStatus
import java.time.ZonedDateTime
import kotlin.reflect.KClass

class EditorCell(
  private val editor: EditorEx,
  private val manager: NotebookCellInlayManager,
  internal var intervalPointer: NotebookIntervalPointer,
  private val viewFactory: (EditorCell) -> EditorCellView,
) : Disposable, UserDataHolder by UserDataHolderBase() {

  val source: String
    get() {
      val document = editor.document
      val startOffset = document.getLineStartOffset(interval.lines.first + 1)
      val endOffset = document.getLineEndOffset(interval.lines.last)
      if (startOffset >= endOffset) return ""  // possible for empty cells
      return document.getText(TextRange(startOffset, endOffset))
    }

  val type: NotebookCellLines.CellType get() = interval.type

  val interval get() = intervalPointer.get() ?: error("Invalid interval")

  var view: EditorCellView? = createView()

  var visible: Boolean = true
    set(value) {
      if (field == value) return
      field = value

      if (value) {
        view?.let {
          disposeView(it)
        }
      }
      else {
        if (view == null) {
          view = createView()
          view?.selected = selected
          gutterAction?.let { view?.setGutterAction(it) }
          view?.updateExecutionStatus(executionCount, progressStatus, executionStartTime, executionEndTime)
        }
      }
    }

  private fun createView(): EditorCellView {
    val view = viewFactory(this).also { Disposer.register(this, it) }
    manager.fireCellViewCreated(view)
    return view
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

  private var gutterAction: AnAction? = null

  private var executionCount: Int? = null

  private var progressStatus: ProgressStatus? = null

  private var executionStartTime: ZonedDateTime? = null

  private var executionEndTime: ZonedDateTime? = null

  override fun dispose() {
    view?.let { disposeView(it) }
  }

  fun update(force: Boolean = false) {
    view?.update(force)
  }

  fun updateInput() {
    view?.updateInput()
  }

  fun onViewportChange() {
    view?.onViewportChanges()
  }

  fun setGutterAction(action: AnAction) {
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
    update(true)
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
}