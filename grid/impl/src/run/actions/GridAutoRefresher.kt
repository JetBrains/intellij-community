package com.intellij.database.run.actions

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.asContextElement
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineSuspender
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.await
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.awt.Component
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

class GridAutoRefresher(private val grid: DataGrid, private var interval: Duration) : Disposable {
  private val manualPauseSuspender = coroutineSuspender()
  private val scope = CoroutineScope(manualPauseSuspender.asContextElement())

  init {
    scope.launch { processAutoRefreshWhenShown() }
  }

  override fun dispose() {
    scope.cancel()
  }

  private suspend fun processAutoRefreshWhenShown() {
    isShowingFlow(this@GridAutoRefresher, grid.panel.component).collectLatest { isShowing ->
      if (isShowing) {
        processAutoRefresh()
      }
    }
  }

  private suspend fun processAutoRefresh() {
    while (true) {
      delay(interval)
      checkCanceled()
      performRefresh()
    }
  }

  private suspend fun performRefresh() {
    withContext(Dispatchers.EDT) {
      if (shouldPause()) {
        setManuallyPaused(true)
        return@withContext
      }
      try {
        reloadData()
      }
      catch (ce: CancellationException) {
        throw ce
      }
      catch (ignored: RuntimeException) {
        setManuallyPaused(true)
      }
    }
  }

  private suspend fun reloadData() {
    GridRequestSource(null).also {
      grid.dataHookup.loader.reloadCurrentPage(it)
    }.actionCallback.await()
  }

  fun isManuallyPaused(): Boolean = manualPauseSuspender.isPaused()

  fun setManuallyPaused(pause: Boolean) {
    if (isManuallyPaused() == pause) return
    if (pause) {
      manualPauseSuspender.pause()
    }
    else {
      manualPauseSuspender.resume()
    }
  }

  private fun shouldPause(): Boolean {
    if (!hasChanges()) return false
    val res = Messages.showYesNoDialog(
      grid.project, DataGridBundle.message("dialog.message.changes.are.submitted.data.will.be.lost.continue"),
      DataGridBundle.message("dialog.title.auto.refresh"),
      DataGridBundle.message("button.refresh"),
      DataGridBundle.message("button.pause.auto.refresh"),
      Messages.getWarningIcon()
    )
    if (res == Messages.NO) {
      return true
    }
    grid.stopEditing()
    return false
  }

  private fun hasChanges(): Boolean {
    return grid.isEditing || grid.dataHookup.mutator?.hasPendingChanges() == true
  }

  companion object {
    private val KEY = Key.create<GridAutoRefresher>("GridAutoRefresher")

    @JvmStatic
    fun getRefresher(dataGrid: DataGrid?): GridAutoRefresher? {
      return KEY[dataGrid]
    }

    private fun setRefresher(dataGrid: DataGrid, r: GridAutoRefresher?) {
      val prev = KEY[dataGrid]
      if (prev != null) {
        Disposer.dispose(prev)
      }
      if (r != null) {
        Disposer.register(dataGrid, r)
      }
      KEY[dataGrid] = r
    }

    @JvmStatic
    fun isEnabled(dataGrid: DataGrid): Boolean {
      return getRefreshInterval(dataGrid) != 0
    }

    @JvmStatic
    fun getRefreshInterval(dataGrid: DataGrid): Int {
      val refresher = getRefresher(dataGrid)
      return getRefreshInterval(refresher)
    }

    private fun getRefreshInterval(refresher: GridAutoRefresher?): Int {
      return refresher?.interval?.toInt(DurationUnit.SECONDS) ?: 0
    }

    @JvmStatic
    fun setRefreshInterval(dataGrid: DataGrid, intervalSec: Int) {
      val refresher = getRefresher(dataGrid)
      if (getRefreshInterval(refresher) == intervalSec) {
        return
      }
      if (intervalSec == 0) {
        setRefresher(dataGrid, null)
      }
      else if (refresher != null) {
        refresher.interval = intervalSec.seconds
      }
      else {
        setRefresher(dataGrid, GridAutoRefresher(dataGrid, intervalSec.seconds))
      }
    }

    private fun isShowingFlow(parentDisposable: Disposable, component: Component): Flow<Boolean> {
      val showing = MutableStateFlow(false)
      Disposer.register(parentDisposable, UiNotifyConnector.installOn(component, object : Activatable {
        override fun showNotify() {
          showing.value = true
        }

        override fun hideNotify() {
          showing.value = false
        }
      }))
      return showing
    }
  }
}