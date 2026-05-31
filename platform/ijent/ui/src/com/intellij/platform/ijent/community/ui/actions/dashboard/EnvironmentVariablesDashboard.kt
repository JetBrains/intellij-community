// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.actions.dashboard

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.util.EnvVariablesTable
import com.intellij.execution.util.EnvironmentVariable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.platform.eel.EelExecApi.EnvironmentVariablesException
import com.intellij.ui.AnActionButtonRunnable
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EnvironmentVariablesDashboard(private val modeProperty: Flow<FetchEnvVarsMode>) {

  class FetchEnvVarsMode(val doFetch: suspend () -> Map<String, String>)

  val component: DialogPanel = panel {
    val envTable = ReadOnlyEnvVariablesTable()

    envTable.component.launchOnShow("update envTable on combobox changes") {
      modeProperty.collectLatest { mode ->
        fetchEnvVars(mode) { result ->
          envTable.applyResult(result)
        }
      }
    }
    row { cell(envTable.component).resizableColumn().align(Align.FILL) }.resizableRow()
  }

  private suspend fun fetchEnvVars(mode: FetchEnvVarsMode, consumer: suspend (EnvFetchResult) -> Unit) {
    consumer(EnvFetchResult(emptyList(), "Loading..."))
    val result = try {
      val envVars = withContext(Dispatchers.Default) {
        mode.doFetch()
      }
      EnvFetchResult(
        envVars.entries.map { (k, v) -> EnvironmentVariable(k, v, true) },
        ExecutionBundle.message("empty.text.no.variables"),
      )
    }
    catch (ex: EnvironmentVariablesException) {
      EnvFetchResult(emptyList(), "Error: ${ex.message ?: ex.toString()}")
    }
    consumer(result)
  }
}

private class ReadOnlyEnvVariablesTable : EnvVariablesTable() {
  override fun createAddAction(): AnActionButtonRunnable? = null
  override fun createRemoveAction(): AnActionButtonRunnable? = null
  override fun createExtraToolbarActions(): Array<AnAction?> = emptyArray()

  fun applyResult(result: EnvFetchResult) {
    setValues(result.vars)
    tableView.emptyText.text = result.emptyText
  }
}

private data class EnvFetchResult(
  val vars: List<EnvironmentVariable>,
  val emptyText: String,
)