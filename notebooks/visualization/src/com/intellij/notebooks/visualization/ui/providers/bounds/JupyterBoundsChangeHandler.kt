package com.intellij.notebooks.visualization.ui.providers.bounds

import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.NotebookCellLinesEvent
import com.intellij.notebooks.visualization.NotebookVisualizationCoroutine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.EventDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import java.beans.PropertyChangeListener
import javax.swing.SwingUtilities

/**
 * Per-editor service on which can one subscribe.
 * It sends the boundsChanged event to the subscribed JupyterBoundsChangeListener.
 *
 * boundsChanged event will be dispatched if
 * - someone directly calls JupyterBoundsChangeHandler.get(editor).boundsChanged()
 * - EditorImpl property was changed
 * - On soft wrap recalculation ends
 * - Folding model change
 * - Inlay model change
 * - Tool window state changes
 */
// Class name does not reflect the functionality of this class.
class JupyterBoundsChangeHandler(val editor: EditorImpl) : Disposable {
  private var isDelayed = false
  private var isShouldBeRecalculated = false

  private val dispatcher = EventDispatcher.create(JupyterBoundsChangeListener::class.java)

  val eventFlow: MutableSharedFlow<Unit> = MutableSharedFlow()

  init {
    editor.addPropertyChangeListener(PropertyChangeListener { _ ->
      boundsChanged()
    }, this)

    editor.softWrapModel.addSoftWrapChangeListener(object : SoftWrapChangeListener {
      override fun softWrapsChanged() = Unit

      override fun recalculationEnds() {
        if (editor.document.isInEventsHandling)
          return

        // WriteAction can not be called from ReadAction
        // but deep inside boundsChanged, in EditorCellOutputView.kt:60, we are calling layout for EditorImpl that requires write action
        // while recalculationEnds called from readAction
        if (ApplicationManager.getApplication().isWriteAccessAllowed)
          boundsChanged()
        else
          schedulePerformPostponed()
      }
    })

    editor.foldingModel.addListener(object : FoldingListener {
      override fun onFoldRegionStateChange(region: FoldRegion) {
        boundsChanged()
      }

      override fun onFoldProcessingEnd() {
        boundsChanged()
      }

      override fun onCustomFoldRegionPropertiesChange(region: CustomFoldRegion, flags: Int) {
        boundsChanged()
      }
    }, this)

    NotebookCellLines.get(editor).intervalListeners.addListener(object : NotebookCellLines.IntervalListener {
      override fun documentChanged(event: NotebookCellLinesEvent) {
        if (event.isIntervalsChanged())
          boundsChanged()
      }
    }, editor.disposable)

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

    editor.project?.messageBus?.connect(this)?.subscribe(
      ToolWindowManagerListener.TOPIC,
      object : ToolWindowManagerListener {
        override fun stateChanged() = boundsChanged()
      }
    )
  }

  override fun dispose(): Unit = Unit

  fun subscribe(parentDisposable: Disposable, listener: JupyterBoundsChangeListener) {
    dispatcher.addListener(listener, parentDisposable)
  }

  fun boundsChanged() {
    if (isDelayed) {
      isShouldBeRecalculated = true
      return
    }
    notifyBoundsChanged()
  }

  private fun notifyBoundsChanged() {
    if (editor.isDisposed)
      return
    dispatcher.multicaster.boundsChanged()

    NotebookVisualizationCoroutine.Utils.launchBackground {
      eventFlow.emit(Unit)
    }
  }

  fun postponeUpdates() {
    isDelayed = true
    isShouldBeRecalculated = false
  }

  fun performPostponed() {
    finishDelayAndDoIfShouldBeRecalculated { notifyBoundsChanged() }
  }

  fun schedulePerformPostponed() {
    finishDelayAndDoIfShouldBeRecalculated {
      SwingUtilities.invokeLater {
        notifyBoundsChanged()
      }
    }
  }

  private fun finishDelayAndDoIfShouldBeRecalculated(block: () -> Unit) {
    isDelayed = false
    if (isShouldBeRecalculated) {
      isShouldBeRecalculated = false
      block()
    }
  }

  companion object {
    private val INSTANCE_KEY = Key<JupyterBoundsChangeHandler>("INLAYS_CHANGE_HANDLER")

    fun install(editor: EditorImpl) {
      val updater = JupyterBoundsChangeHandler(editor)
      Disposer.register(editor.disposable, updater)
      editor.putUserData(INSTANCE_KEY, updater)
    }

    fun get(editor: Editor): JupyterBoundsChangeHandler = INSTANCE_KEY.get(editor)
  }
}