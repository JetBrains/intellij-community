package com.intellij.database.run.ui

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.extractors.DataExtractor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ConcurrencyUtil
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class Aggregator(private val grid: DataGrid,
                 private val extractor: DataExtractor,
                 private val simpleName: String,
                 private val name: String) {
  private val executor = if (ApplicationManager.getApplication().isUnitTestMode) ConcurrencyUtil.newSameThreadExecutorService()
  else ConcurrencyUtil.newSingleThreadExecutor("Aggregator script executor")
  private var lastTask: Future<*>? = null
  private var result: Any = ""

  fun update(): CompletableFuture<AggregationResult> {
    assert(ApplicationManager.getApplication().isDispatchThread)
    lastTask?.cancel(true)
    result = ""
    val completableFuture = CompletableFuture<AggregationResult>()
    val task: Future<Boolean> = executor.submit(Callable {
      var th: Throwable? = null
      val text: @NlsContexts.Label String =
      try {
        if (grid.selectionModel.selectedColumnCount * grid.selectionModel.selectedRowCount == 0) {
           DataGridBundle.message("label.aggregator.not.enough.values")
        }
        else {
          GridUtil.extractSelectedValues(grid, extractor)
        }
      }
      catch (e: Throwable) {
        th = e
         e.localizedMessage.replace("com.intellij.ide.script.IdeScriptException: ", "")
      }
      val result = AggregationResult(text, th)
      result.setDecorateState(result.isFullTextShown())
      completableFuture.complete(result)
    })
    lastTask = task
    return completableFuture
  }

  @NlsSafe
  fun getSimpleName(): String {
    return simpleName
  }

  fun getName(): String {
    return name
  }
}

class AggregationResult(val text: @NlsContexts.Label String,
                        val exception: Throwable?) {

  private var isFullTextShown: Boolean = false

  fun isScriptExceptionHappened(): Boolean = exception != null

  fun getRowsCount(): Int {
    return if (!isFullTextShown) 1
    else 1 + StringUtil.countChars(text, '\n')
  }

  fun processMouseClickEvent(e: MouseEvent, rectangle: Rectangle, panel: AggregateView.AggregatorViewPanel): Boolean {
    return if (text.contains("\n") && (e.clickCount % 2 == 0 || panel.isClickOnButton(e, rectangle))) {
      setDecorateState(!isFullTextShown)
      false
    }
    else {
      true
    }
  }

  fun setDecorateState(show: Boolean) {
    isFullTextShown = if (text.contains("\n")) show
    else false
  }

  fun setFullTextShown(show: Boolean) {
    isFullTextShown = if (exception != null) false
    else show
  }

  fun isFullTextShown(): Boolean = isFullTextShown
}
