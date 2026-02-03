package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Serializable
data class ShowMessageRequestParams(
    val type: MessageType,
    val message: String,
    val actions: List<MessageActionItem>?,
)

@Serializable
data class MessageActionItem(
    val title: String,
)

@Serializable
data class WorkDoneProgressCreateParams(
    val token: ProgressToken,
)

@Serializable
data class WorkDoneProgressCancelParams(
    val token: ProgressToken,
)

object Window {
    val ShowMessageRequest: RequestType<ShowMessageRequestParams, MessageActionItem?, Unit> =
        RequestType("window/showMessageRequest", ShowMessageRequestParams.serializer(), MessageActionItem.serializer().nullable, Unit.serializer())

    val CreateProgress: RequestType<WorkDoneProgressCreateParams, Unit, Unit> =
      RequestType("window/workDoneProgress/create", WorkDoneProgressCreateParams.serializer(), Unit.serializer(), Unit.serializer())

    val CancelProgress: NotificationType<WorkDoneProgressCancelParams> =
        NotificationType("window/workDoneProgress/cancel", WorkDoneProgressCancelParams.serializer())
}