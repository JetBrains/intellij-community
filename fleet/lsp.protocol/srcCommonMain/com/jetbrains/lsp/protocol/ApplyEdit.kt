package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class ApplyWorkspaceEditParams(
    val label: String?,
    val edit: WorkspaceEdit,
)

@Serializable
data class ApplyWorkspaceEditResult(
    /**
     * Indicates whether the edit was applied or not.
     */
    val applied: Boolean,

    /**
     * An optional textual description for why the edit was not applied.
     * This may be used by the server for diagnostic logging or to provide
     * a suitable error for a request that triggered the edit.
     */
    val failureReason: String?,

    /**
     * Depending on the client's failure handling strategy `failedChange`
     * might contain the index of the change that failed. This property is
     * only available if the client signals a `failureHandling` strategy
     * in its client capabilities.
     */
    val failedChanges: Int?,
)

object ApplyEditRequests {
    val ApplyEdit: RequestType<ApplyWorkspaceEditParams, ApplyWorkspaceEditResult, Unit> =
        RequestType("workspace/applyEdit", ApplyWorkspaceEditParams.serializer(), ApplyWorkspaceEditResult.serializer(), Unit.serializer())
}