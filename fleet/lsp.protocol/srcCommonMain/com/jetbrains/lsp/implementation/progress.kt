package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.*
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

suspend fun LspClient.withProgress(
  request: WorkDoneProgressParams,
  beginTitle: String,
  body: suspend CoroutineScope.(ProgressReporter) -> WorkDoneProgress.End,
) {
  coroutineScope {
    when (val token = request.workDoneToken) {
      null -> body(ProgressReporter.NOOP)
      else -> {
        val beginProgress = WorkDoneProgress.Begin(title = beginTitle)
        notify(
          LSP.ProgressNotificationType,
          ProgressParams(token, LSP.json.encodeToJsonElement(WorkDoneProgress.serializer(), beginProgress)),
        )

        val state = MutableStateFlow<WorkDoneProgress?>(null)
        val endProgress = launch {
          state.filterNotNull().collect { p ->
            notify(LSP.ProgressNotificationType, ProgressParams(token, LSP.json.encodeToJsonElement(WorkDoneProgress.serializer(), p)))
            delay(100.milliseconds)
          }
        }.use {
          body(ProgressReporter { p -> state.value = p })
        }

        notify(
          LSP.ProgressNotificationType,
          ProgressParams(token, LSP.json.encodeToJsonElement(WorkDoneProgress.serializer(), endProgress))
        )
      }
    }
  }
}