package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable

@Serializable
data class LogMessageParams(
    /**
     * The message type. See {@link MessageType}
     */
    val type: MessageType,

    /**
     * The actual message
     */
    val message: String,
)

@Serializable
data class ShowMessageParams(
    /**
     * The message type. See {@link MessageType}
     */
    val type: MessageType,

    /**
     * The actual message
     */
    val message: String,
)

@Serializable(with = MessageType.Serializer::class)
enum class MessageType(val value: Int) {
    /**
     * An error message.
     */
    Error(1),

    /**
     * A warning message.
     */
    Warning(2),

    /**
     * An information message.
     */
    Info(3),

    /**
     * A log message.
     */
    Log(4),

    /**
     * A debug message.
     *
     * @since 3.18.0
     * @proposed
     */
    Debug(5),

    ;

   internal class Serializer : EnumAsIntSerializer<MessageType>(
       serialName = MessageType::class.simpleName!!,
       serialize = MessageType::value,
       deserialize = { entries[it - 1] },
    )
}

val LogMessageNotification: NotificationType<LogMessageParams> = NotificationType("window/logMessage", LogMessageParams.serializer())
val ShowMessageNotification: NotificationType<ShowMessageParams> = NotificationType("window/showMessage", ShowMessageParams.serializer())