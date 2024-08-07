package org.jetbrains.plugins.notebooks.visualization.inlay

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLinesEvent
import java.beans.PropertyChangeListener

class JupyterBoundsChangeHandler(val editor: EditorImpl) : Disposable {
  private var isDelayed = false
  private var isShouldBeRecalculated = false

  private val dispatcher = EventDispatcher.create(JupyterBoundsChangeListener::class.java)


  init {
    editor.addPropertyChangeListener(PropertyChangeListener { _ ->
      boundsChanged()
    }, this)


    editor.softWrapModel.addSoftWrapChangeListener(object : SoftWrapChangeListener {
      override fun softWrapsChanged() {
      }

      override fun recalculationEnds() {
        if (editor.document.isInEventsHandling)
          return
        boundsChanged()
      }
    })

    editor.foldingModel.addListener(object : FoldingListener {
      override fun onFoldProcessingEnd() {
        boundsChanged()
      }
    }, this)

    NotebookCellLines.get(editor).intervalListeners.addListener(object : NotebookCellLines.IntervalListener {
      override fun documentChanged(event: NotebookCellLinesEvent) {
        if (event.isIntervalsChanged())
          boundsChanged()
      }
    })

    editor.inlayModel.addListener(object : InlayModel.Listener {
      override fun onBatchModeFinish(editor: Editor) = boundsChanged()
      override fun onAdded(inlay: Inlay<*>) {
        if (inlay.heightInPixels == 0)
          return

        recalculateIfNotBatch()
      }

      override fun onUpdated(inlay: Inlay<*>, changeFlags: Int) {
        if ((changeFlags and InlayModel.ChangeFlags.HEIGHT_CHANGED) != 0)
          recalculateIfNotBatch()
      }

      override fun onRemoved(inlay: Inlay<*>) = recalculateIfNotBatch()

      private fun recalculateIfNotBatch() {
        if (editor.inlayModel.isInBatchMode)
          return
        if (editor.document.isInEventsHandling)
          return
        boundsChanged()
      }
    }, this)
  }

  override fun dispose() {}

  fun subscribe(listener: JupyterBoundsChangeListener) {
    dispatcher.addListener(listener)
  }

  fun unsubscribe(listener: JupyterBoundsChangeListener) {
    dispatcher.removeListener(listener)
  }


  fun boundsChanged() {
    if (isDelayed) {
      isShouldBeRecalculated = true
      return
    }
    dispatcher.multicaster.boundsChanged()
  }

  fun postponeUpdates() {
    isDelayed = true
    isShouldBeRecalculated = false
  }

  fun performPostponed() {
    isDelayed = false
    if (isShouldBeRecalculated) {
      boundsChanged()
    }
  }

  companion object {
    private val INSTANCE_KEY = Key<JupyterBoundsChangeHandler>("INLAYS_CHANGE_HANDLER")

    fun install(editor: EditorImpl) {
      val updater = JupyterBoundsChangeHandler(editor).also { Disposer.register(editor.disposable, it) }
      editor.putUserData(INSTANCE_KEY, updater)
    }

    fun get(editor: Editor): JupyterBoundsChangeHandler? = INSTANCE_KEY.get(editor)
  }
}