@file:JvmName("AggregatorWidgetLoader")

package com.intellij.database.run.ui

import com.intellij.database.datagrid.AggregatorWidget
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.extensions.ExtensionScriptsUtil
import com.intellij.database.extensions.ExtractorScripts
import com.intellij.database.extractors.DataAggregatorFactory
import com.intellij.database.extractors.ExtractorsHelper
import com.intellij.openapi.application.EDT
import com.intellij.openapi.wm.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun loadWidgetAggregatorAsync(
  grid: DataGrid,
  script: DataAggregatorFactory,
  helper: TableAggregatorWidgetHelper,
): Job {
  return grid.coroutineScope.launch {
    val config = withContext(Dispatchers.EDT) {
      val script = ExtractorScripts.findAggregatorScript(script.name)
      script?.let { ExtensionScriptsUtil.prepareScript(it) }
      ExtractorsHelper.getInstance(grid).createExtractorConfig(grid, grid.objectFormatter)
    }
    // building the aggregator may load a heavy script engine (e.g. Groovy), which must not run on the EDT
    val extractor = withContext(Dispatchers.Default) { script.buildAggregator(config) } ?: return@launch
    withContext(Dispatchers.EDT) {
      helper.aggregator = Aggregator(grid, extractor, script.simpleName, script.name)
      refreshAggregatorWidget(grid)
    }
  }
}

private fun refreshAggregatorWidget(grid: DataGrid) {
  val statusBar = WindowManager.getInstance().getStatusBar(grid.panel.component, grid.project) ?: return
  statusBar.updateWidget(AggregatorWidget.ID)
}
