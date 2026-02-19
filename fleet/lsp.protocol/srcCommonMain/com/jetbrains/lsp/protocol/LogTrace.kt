package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable

/**
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#logTrace">logTraceParams (LSP spec)</a>
 */
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
  val verbose: String?,
)

@Serializable
data class SetTraceParams(
  /**
   * The new value that should be assigned to the trace setting.
   */
  val value: TraceValue,
)

/**
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#logTrace">$/logTrace (LSP spec)</a>
 */
val LogTraceNotificationType: NotificationType<LogTraceParams> = NotificationType(
  method = "$/logTrace",
  paramsSerializer = LogTraceParams.serializer(),
)

/**
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#setTrace">$/setTrace (LSP spec)</a>
 */
val SetTraceNotificationType: NotificationType<SetTraceParams> = NotificationType(
  method = "$/setTrace",
  paramsSerializer = SetTraceParams.serializer(),
)
