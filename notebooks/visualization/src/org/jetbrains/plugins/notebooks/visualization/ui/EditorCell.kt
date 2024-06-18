package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayController
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer
import kotlin.reflect.KClass

class EditorCell(
  private val editor: EditorEx,
  internal var intervalPointer: NotebookIntervalPointer,
  private val viewFactory: (EditorCell) -> EditorCellView
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

  private var _visible = true
  val visible: Boolean
    get() = _visible

  private var _selected = false

  var selected: Boolean
    get() = _selected
    set(value) {
      if (_selected != value) {
        _selected = value
        updateSelection(value)
      }
    }

  private fun updateSelection(value: Boolean) {
    view?.updateSelection(value)
  }

  val interval get() = intervalPointer.get() ?: error("Invalid interval")

  var view: EditorCellView? = viewFactory(this)

  private var gutterAction: AnAction? = null

  fun hide() {
    _visible = false
    view?.let { Disposer.dispose(it) }
    view = null
  }

  fun show() {
    _visible = true
    if (view == null) {
      view = viewFactory(this).also { Disposer.register(this, it) }
      view?.updateSelection(_selected)
      gutterAction?.let { view?.setGutterAction(it) }
    }
  }

  override fun dispose() {
    view?.let { Disposer.dispose(it) }
  }

  fun update(force: Boolean = false) {
    view?.update(force)
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

  @PublishedApi internal fun createLazyControllers(factory: NotebookCellInlayController.LazyFactory) {
    factory.cellOrdinalsInCreationBlock.add(interval.ordinal)
    update(true)
    factory.cellOrdinalsInCreationBlock.remove(interval.ordinal)
  }

  @PublishedApi internal fun <T: NotebookCellInlayController> getLazyFactory(type: KClass<T>): NotebookCellInlayController.LazyFactory? {
    return NotebookCellInlayController.Factory.EP_NAME.extensionList
      .filterIsInstance<NotebookCellInlayController.LazyFactory>()
      .firstOrNull { it.getControllerClass() == type.java }
  }
}