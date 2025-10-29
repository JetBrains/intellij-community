package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.*
import com.jetbrains.lsp.implementation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlin.time.Duration.Companion.milliseconds

fun interface ProgressReporter {
  companion object {
    val NOOP: ProgressReporter = ProgressReporter {}
  }
  fun report(progress: WorkDoneProgress)
}

suspend fun<T> LspClient.withProgress(
  request: WorkDoneProgressParams,
  body: suspend CoroutineScope.(ProgressReporter) -> T
): T =
  coroutineScope {
    when (val token = request.workDoneToken) {
      null -> body(ProgressReporter.NOOP)
      else -> {
        val state = MutableStateFlow<WorkDoneProgress?>(null)
        launch {
          state.filterNotNull().collect { p ->
            notify(LSP.ProgressNotificationType, ProgressParams(token, LSP.json.encodeToJsonElement(WorkDoneProgress.serializer(), p)))
            delay(100.milliseconds)
          }
        }.use {
          body(ProgressReporter { p -> state.value = p })
        }
      }
    }
  }