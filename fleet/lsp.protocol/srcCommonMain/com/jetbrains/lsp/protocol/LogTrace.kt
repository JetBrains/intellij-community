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

@Serializable
data class SetTraceParams(
  /**
   * The new value that should be assigned to the trace setting.
   */
  val value: TraceValue,
)

val LogTraceNotificationType: NotificationType<LogTraceParams> =
    NotificationType("\$/logTrace", LogTraceParams.serializer())

val SetTraceNotificationType: NotificationType<SetTraceParams> =
    NotificationType("\$/setTrace", SetTraceParams.serializer())