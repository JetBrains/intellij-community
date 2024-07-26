// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.logging

import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.trace.ReadableSpan
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import org.jetbrains.intellij.build.logging.LogMessage.Kind.*

class TeamCityBuildMessageLogger : BuildMessageLogger() {
  companion object {
    @JvmField
    val FACTORY: () -> BuildMessageLogger = ::TeamCityBuildMessageLogger

    private fun print(messageId: String, argument: String) {
      println(SpanAwareServiceMessage(messageId, argument))
    }

    @ApiStatus.Internal
    fun print(messageId: String, vararg attributes: Pair<String, String>) {
      println(SpanAwareServiceMessage(messageId, *attributes))
    }

    /**
     * [io.opentelemetry.api.trace.Span]-aware [ServiceMessage].
     *
     * [ServiceMessage.setFlowId] is called with [io.opentelemetry.api.trace.SpanContext.getSpanId].
     *
     * See [the docs](https://www.jetbrains.com/help/teamcity/service-messages.html#Message+FlowId).
     */
    @ApiStatus.Internal
    class SpanAwareServiceMessage : ServiceMessage {
      constructor(messageName: String, argument: String) : super(messageName, argument)
      constructor(messageName: String, vararg attributes: Pair<String, String>) : super(messageName, mapOf(*attributes))

      init {
        setFlowId(Span.current().spanContext.spanId)
      }
    }

    /**
     * Wraps a [span] into a TeamCity flow linked to a parent flow of a parent span.
     * Flows are not displayed in a TeamCity build log.
     */
    @ApiStatus.Internal
    inline fun <T> withFlow(span: Span, operation: () -> T): T {
      if (!TeamCityHelper.isUnderTeamCity) {
        return operation()
      }
      val parentFlowId = (span as? ReadableSpan)?.parentSpanContext?.spanId
      print(ServiceMessageTypes.FLOW_STARTED, "parent" to "$parentFlowId")
      return try {
        operation()
      }
      finally {
        print(ServiceMessageTypes.FLOW_FINSIHED)
      }
    }
  }

  override fun processMessage(message: LogMessage) {
    when (message.kind) {
      INFO -> print(ServiceMessageTypes.MESSAGE, "text" to message.text)
      WARNING -> print(ServiceMessageTypes.MESSAGE, "text" to message.text, "status" to "WARNING")
      ERROR -> {
        val messageText = message.text.trim()
        val lineEnd = messageText.indexOf('\n')
        if (lineEnd != -1) {
          print(ServiceMessageTypes.MESSAGE,
                "text" to messageText.substring(0, lineEnd),
                "errorDetails" to messageText.substring(lineEnd + 1),
                "status" to "ERROR")
        }
        else {
          print(ServiceMessageTypes.MESSAGE, "text" to messageText, "status" to "ERROR")
        }
      }
      PROGRESS -> print(ServiceMessageTypes.PROGRESS_MESSAGE, message.text)
      BLOCK_STARTED -> print(ServiceMessageTypes.BLOCK_OPENED, "name" to message.text)
      BLOCK_FINISHED -> print(ServiceMessageTypes.BLOCK_CLOSED, "name" to message.text)
      ARTIFACT_BUILT -> print(ServiceMessageTypes.PUBLISH_ARTIFACTS, message.text)
      BUILD_STATUS -> print(ServiceMessageTypes.BUILD_STATUS, "text" to message.text)
      BUILD_STATUS_CHANGED_TO_SUCCESSFUL -> print(ServiceMessageTypes.BUILD_STATUS, "status" to "SUCCESS", "text" to message.text)
      STATISTICS -> {
        val index = message.text.indexOf('=')
        val key = message.text.substring(0, index)
        val value = message.text.substring(index + 1)
        print(ServiceMessageTypes.BUILD_STATISTIC_VALUE, "key" to key, "value" to value)
      }
      SET_PARAMETER -> {
        val index = message.text.indexOf('=')
        val name = message.text.substring(0, index)
        val value = message.text.substring(index + 1)
        print(ServiceMessageTypes.BUILD_SET_PARAMETER, "name" to name, "value" to value)
      }
      COMPILATION_ERRORS -> {
        val compiler = (message as CompilationErrorsLogMessage).compilerName
        print(ServiceMessageTypes.COMPILATION_STARTED, "compiler" to compiler)
        message.errorMessages.forEach {
          print(ServiceMessageTypes.MESSAGE, "text" to it, "status" to "ERROR")
        }
        print(ServiceMessageTypes.COMPILATION_FINISHED, "compiler" to compiler)
      }
      DEBUG -> {} //debug messages are printed to a separate file available in the build artifacts
      BUILD_PROBLEM -> { // The text is limited to 4000 symbols and will be truncated if the limit is exceeded
        check(message is BuildProblemLogMessage) {
          "Unexpected build problem message type: ${message::class.java.canonicalName}"
        }
        if (message.identity != null) {
          print(ServiceMessageTypes.BUILD_PORBLEM, "description" to message.text, "identity" to message.identity)
        }
        else {
          print(ServiceMessageTypes.BUILD_PORBLEM, "description" to message.text)
        }
      }
      BUILD_CANCEL -> print(ServiceMessageTypes.BUILD_STOP, "comment" to message.text, "readdToQueue" to "false")
    }
  }
}
