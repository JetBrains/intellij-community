package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.ClientCapabilities
import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.ProgressParams
import com.jetbrains.lsp.protocol.ProgressToken
import com.jetbrains.lsp.protocol.StringOrInt
import com.jetbrains.lsp.protocol.Window
import com.jetbrains.lsp.protocol.WorkDoneProgress
import com.jetbrains.lsp.protocol.WorkDoneProgressCreateParams
import fleet.util.UID
import fleet.util.async.catching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

fun interface ProgressReporter {
    companion object {
        val NOOP: ProgressReporter = ProgressReporter {}
    }

    fun report(progress: WorkDoneProgress)
}

suspend fun<T> LspClient.withServerInitiatedProgress(
    capabilities: ClientCapabilities,
    beginTitle: String,
    body: suspend CoroutineScope.(ProgressReporter) -> T,
): T {
    val token = when {
        capabilities.window?.workDoneProgress == true -> {
            val token = ProgressToken(StringOrInt.string(UID.random().id))
            catching {
                request(Window.CreateProgress, WorkDoneProgressCreateParams(token))
            }
            token
        }

        else -> null
    }
    return withProgress(token, beginTitle) { progress ->
        body(progress)
    }
}

suspend fun<T> LspClient.withProgress(
    token: ProgressToken?,
    beginTitle: String,
    body: suspend CoroutineScope.(ProgressReporter) -> T,
): T =
    coroutineScope {
        when (token) {
            null -> body(ProgressReporter.NOOP)
            else -> {
                val beginProgress = WorkDoneProgress.Begin(title = beginTitle)
                notify(
                    LSP.ProgressNotificationType,
                    ProgressParams(token, LSP.json.encodeToJsonElement(WorkDoneProgress.serializer(), beginProgress)),
                )

                val state = MutableStateFlow<WorkDoneProgress?>(null)
                try {
                    launch {
                        state.filterNotNull().collect { p ->
                            notify(
                                LSP.ProgressNotificationType,
                                ProgressParams(token, LSP.json.encodeToJsonElement(WorkDoneProgress.serializer(), p))
                            )
                            delay(100.milliseconds)
                        }
                    }.use {
                        body(ProgressReporter { p -> state.value = p })
                    }
                } finally {
                    notify(
                        LSP.ProgressNotificationType,
                        ProgressParams(token, LSP.json.encodeToJsonElement(WorkDoneProgress.serializer(), WorkDoneProgress.End()))
                    )
                }
            }
        }
    }