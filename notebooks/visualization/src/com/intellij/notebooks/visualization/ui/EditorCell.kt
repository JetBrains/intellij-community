package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.NotebookCellInlayController
import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.NotebookCellLines.CellType
import com.intellij.notebooks.visualization.NotebookCellLines.Interval
import com.intellij.notebooks.visualization.NotebookIntervalPointer
import com.intellij.notebooks.visualization.UpdateContext
import com.intellij.notebooks.visualization.outputs.NotebookOutputDataKey
import com.intellij.notebooks.visualization.ui.cell.frame.EditorCellFrameManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import java.time.ZonedDateTime
import kotlin.reflect.KClass


class EditorCell(
  val notebook: EditorNotebook,
  var intervalPointer: NotebookIntervalPointer,
  val editor: EditorImpl,
) : Disposable, UserDataHolder by UserDataHolderBase() {
  val isUnfolded: AtomicBooleanProperty = AtomicBooleanProperty(true)
  val isSelected: AtomicBooleanProperty = AtomicBooleanProperty(false)
  val isUnderDiff: AtomicBooleanProperty = AtomicBooleanProperty(false)
  val isHovered: AtomicBooleanProperty = AtomicBooleanProperty(false)

  //Enable NotebookVisibleCellsBatchUpdater if this field is required
  //val isInViewportRectangle: AtomicBooleanProperty = AtomicBooleanProperty(false)

  val source: AtomicProperty<String> = AtomicProperty(interval.getContentText(editor))
  val gutterAction: AtomicProperty<AnAction?> = AtomicProperty(null)
  val executionStatus: AtomicProperty<ExecutionStatus> = AtomicProperty<ExecutionStatus>(ExecutionStatus())

  val cellFrameManager: EditorCellFrameManager? = EditorCellFrameManager.create(this)?.also {
    Disposer.register(this, it)
  }

  val outputs: EditorCellOutputs = EditorCellOutputs(this)

  val interval: Interval
    get() = intervalPointer.get() ?: error("Invalid interval")
  val intervalOrNull: Interval?
    get() = intervalPointer.get()
  val type: CellType
    get() = interval.type
  val view: EditorCellView?
    get() = NotebookCellInlayManager.get(editor)?.views[this]


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
    source.set(interval.getContentText(editor))
  }

  fun onViewportChange() {
    view?.onViewportChanges()
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

  companion object {
    private val CELL_EXTENSION_CONTAINER_KEY = Key<MutableMap<KClass<*>, EditorCellExtension>>("CELL_EXTENSION_CONTAINER_KEY")
  }
}