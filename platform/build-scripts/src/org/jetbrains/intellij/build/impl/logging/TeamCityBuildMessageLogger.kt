// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes
import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.BuildProblemLogMessage
import org.jetbrains.intellij.build.CompilationErrorsLogMessage
import org.jetbrains.intellij.build.LogMessage
import org.jetbrains.intellij.build.LogMessage.Kind.*
import java.io.PrintStream
import java.util.concurrent.ConcurrentLinkedDeque

class TeamCityBuildMessageLogger : BuildMessageLogger() {
  companion object {
    @JvmField
    val FACTORY: () -> BuildMessageLogger = ::TeamCityBuildMessageLogger

    private val out: PrintStream? = System.out

    private fun print(messageId: String, argument: String) {
      printMessageText(ServiceMessage.asString(messageId, argument))
    }

    private fun print(messageId: String, vararg attributes: Pair<String, String>) {
      check(attributes.any()) { messageId }
      printMessageText(ServiceMessage.asString(messageId, mapOf(*attributes)))
    }

    private fun printMessageText(message: String) {
      out?.println(message)
    }
  }

  private val delayedBlockStartMessages = ConcurrentLinkedDeque<LogMessage>()

  override fun processMessage(message: LogMessage) {
    when (message.kind) {
      INFO -> logPlainMessage(message, "")
      WARNING -> logPlainMessage(message, "WARNING")
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
      BLOCK_STARTED -> delayedBlockStartMessages.addLast(message)
      BLOCK_FINISHED -> {
        if (!dropDelayedBlockStartMessageIfSame(message)) {
          print(ServiceMessageTypes.BLOCK_CLOSED, "name" to message.text)
        }
      }
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
      BUILD_PROBLEM -> {
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
    }
  }

  private fun logPlainMessage(message: LogMessage, status: String) {
    printDelayedBlockStartMessages()
    if (status.isNotBlank()) {
      print(ServiceMessageTypes.MESSAGE, "text" to message.text, "status" to status)
    }
    else {
      printMessageText(message.text)
    }
  }

  private fun print(messageId: String, argument: String) {
    printDelayedBlockStartMessages()
    TeamCityBuildMessageLogger.print(messageId, argument)
  }


  private fun printDelayedBlockStartMessages() {
    var message = delayedBlockStartMessages.pollFirst()
    while (message != null) {
      print(ServiceMessageTypes.BLOCK_OPENED, "name" to message.text)
      message = delayedBlockStartMessages.pollFirst()
    }
  }

  private fun dropDelayedBlockStartMessageIfSame(message: LogMessage): Boolean {
    var last = delayedBlockStartMessages.peekLast()
    if (last == null) return false
    if (message.text != last.text) {
      return false
    }
    last = delayedBlockStartMessages.pollLast()
    if (message.text != last.text) {
      // it's different since peek, return it back, hopefully no one notice that
      delayedBlockStartMessages.addLast(last)
      return false
    }
    return true
  }
}
