package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.ClientCapabilities
import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.ProgressParams
import com.jetbrains.lsp.protocol.ProgressToken
import com.jetbrains.lsp.protocol.StringOrInt
import com.jetbrains.lsp.protocol.Window
import com.jetbrains.lsp.protocol.WorkDoneProgress
import com.jetbrains.lsp.protocol.WorkDoneProgressCreateParams
import fleet.multiplatform.shims.MultiplatformConcurrentHashMap
import fleet.util.UID
import fleet.util.async.catching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

fun interface ProgressReporter {
    companion object {
        val NOOP: ProgressReporter = ProgressReporter {}
    }

    fun report(progress: WorkDoneProgress)
}

suspend fun <T> LspClient.withProgress(
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
                        ProgressParams(
                            token,
                            LSP.json.encodeToJsonElement(WorkDoneProgress.serializer(), WorkDoneProgress.End())
                        )
                    )
                }
            }
        }
    }


interface ServerInitiatedProgresses {
    fun <T> asyncWithProgress(
        scope: CoroutineScope,
        title: String,
        cancellable: Boolean,
        body: suspend CoroutineScope.(ProgressReporter) -> T
    ): Deferred<T>
}

fun LspHandlersBuilder.serverInitiatedProgresses(
    client: LspClient,
    capabilities: ClientCapabilities
): ServerInitiatedProgresses =
    when {
        capabilities.window?.workDoneProgress == true -> {
            val cancellableJobs = MultiplatformConcurrentHashMap<ProgressToken, Job>()
            notification(Window.CancelProgress) { n ->
                cancellableJobs[n.token]?.let { job ->
                    job.cancel(CancellationException("Canceled by lsp client"))
                    job.join()
                }
            }
            object : ServerInitiatedProgresses {
                override fun <T> asyncWithProgress(
                    scope: CoroutineScope,
                    title: String,
                    cancellable: Boolean,
                    body: suspend CoroutineScope.(ProgressReporter) -> T
                ): Deferred<T> =
                    scope.async {
                        val token = ProgressToken(StringOrInt.string(UID.random().id))
                        catching {
                            client.request(Window.CreateProgress, WorkDoneProgressCreateParams(token))
                        }
                        if (cancellable) {
                            cancellableJobs[token] = coroutineContext.job
                        }
                        try {
                            client.withProgress(token, title) { reporter ->
                                body(reporter)
                            }
                        } finally {
                            cancellableJobs.remove(token)
                        }
                    }
            }
        }

        else -> {
            object : ServerInitiatedProgresses {
                override fun <T> asyncWithProgress(
                    scope: CoroutineScope,
                    title: String,
                    cancellable: Boolean,
                    body: suspend CoroutineScope.(ProgressReporter) -> T
                ): Deferred<T> =
                    scope.async {
                        body(ProgressReporter.NOOP)
                    }
            }
        }
    }
