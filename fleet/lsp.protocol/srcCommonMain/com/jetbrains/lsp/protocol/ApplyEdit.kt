package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

/**
 * The workspace/applyEdit request is sent from the server to the client to modify resource on the client side.
 */
@Serializable
data class ApplyWorkspaceEditParams(
    /**
     * An optional label of the workspace edit. This label is
     * presented in the user interface for example on an undo
     * stack to undo the workspace edit.
     */
    val label: String?,

    /**
     * The edits to apply.
     */
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