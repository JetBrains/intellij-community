package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable

@Serializable
data class LogTraceParams(
    /**
     * The message to be logged.
     */
    val message: String,
    /**
     * Additional information that can be computed if the `trace` configuration
     * is set to `"verbose"`
     */
    val verbose: String?
)

val LogTraceNotificationType: NotificationType<LogTraceParams> =
    NotificationType("\$/logTrace", LogTraceParams.serializer())

val SetTraceNotificationType: NotificationType<TraceValue> =
    NotificationType("\$/setTrace", TraceValue.serializer())