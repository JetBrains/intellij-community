package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.LSP.ProgressNotificationType
import com.jetbrains.lsp.protocol.ProgressParams
import com.jetbrains.lsp.protocol.ProgressToken
import fleet.util.async.chunkedByTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * Streams results using partial results protocol if possible, otherwise collects all results and returns them directly.
 *
 * @param lspClient The LSP client to send notifications to
 * @param partialResultToken Token for partial results reporting, if null direct response will be used
 * @param resultSerializer Serializer for the result type
 * @return Empty list if streaming was used, otherwise collected results
 */
suspend fun <R> Flow<R>.streamResultsIfPossibleOrRespondDirectly(
    lspClient: LspClient,
    partialResultToken: ProgressToken?,
    resultSerializer: KSerializer<R>,
): List<R> {
    if (partialResultToken == null) {
        return this.toList()
    } else {
        streamResults(lspClient, partialResultToken, resultSerializer)
        // protocol requires returning an empty result when progress is used
        return emptyList()
    }
}

/**
 * Streams results to the LSP client using progress notifications.
 *
 * @param lspClient The LSP client to send notifications to
 * @param partialResultToken Token for partial results reporting
 * @param resultSerializer Serializer for the result type
 */
suspend fun <R> Flow<R>.streamResults(
    lspClient: LspClient,
    partialResultToken: ProgressToken,
    resultSerializer: KSerializer<R>,
) {
    // `chunkedByTimeout` to ensure that we do not spam the client with too many progress notifications.
    chunkedByTimeout(BUFFERING_TIME_BETWEEN_SENDING_BATCH_RESULTS_MS)
        .collect { partialResult ->
            if (partialResult.isEmpty()) return@collect
            lspClient.notify(
                ProgressNotificationType,
                ProgressParams(
                    partialResultToken,
                    LSP.json.encodeToJsonElement(ListSerializer(resultSerializer), partialResult),
                )
            )
        }
}


/**
 * Default time interval in milliseconds between sending batched results to the client.
 */
private const val BUFFERING_TIME_BETWEEN_SENDING_BATCH_RESULTS_MS = 50L
