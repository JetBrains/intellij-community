// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging

import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.CompilationErrorsLogMessage
import org.jetbrains.intellij.build.LogMessage
import org.jetbrains.intellij.build.LogMessage.Kind.*
import org.jetbrains.intellij.build.impl.BuildUtils
import java.io.PrintStream

import java.util.concurrent.ConcurrentLinkedDeque

class TeamCityBuildMessageLogger : BuildMessageLogger() {
  companion object {
    @JvmField
    val FACTORY: () -> BuildMessageLogger = ::TeamCityBuildMessageLogger

    private const val ANT_OUTPUT_PREFIX = "###<<<>>>###:" //copied from jetbrains.buildServer.agent.ant.ServiceMessageBuildProgressLogger
    private val out: PrintStream? = System.out

    private fun doPrintTeamCityMessage(messageId: String, messageArguments: String) {
      printMessageText("##teamcity[$messageId $messageArguments]")
    }

    private fun printMessageText(message: String) {
      if (BuildUtils.isUnderJpsBootstrap) {
        // under jps-bootstrap we're logging directly to teamcity
        // so special prefixes are not required
        out?.println(message)
        return
      }

      if (message.contains(ANT_OUTPUT_PREFIX)) {
        out?.println(message)
      }
      else {
        //TeamCity will ignore an output line if it doesn't contain the prefix so we need to add it manually
        out?.println("$ANT_OUTPUT_PREFIX$message")
      }
    }

    private fun escapeChar(c: Char): Char =
      when (c) {
        '\n' -> 'n'
        '\r' -> 'r'
        '\u0085' -> 'x' // next-line character
        '\u2028' -> 'l' // line-separator character
        '\u2029' -> 'p' // paragraph-separator character
        '|' -> '|'
        '\'' -> '\''
        '[' -> '['
        ']' -> ']'
        else -> 0.toChar()
      }

    private fun escape(text: String): String {
      val escaped = StringBuilder()
      for (c: Char in text.toCharArray()) {
        val escChar = escapeChar(c)
        if (escChar == 0.toChar()) {
          escaped.append(c)
        }
        else {
          escaped.append('|').append(escChar)
        }
      }

      return escaped.toString()
    }
  }

  private val delayedBlockStartMessages = ConcurrentLinkedDeque<LogMessage>()

  override fun processMessage(message: LogMessage) {
    when (message.kind) {
      INFO -> logPlainMessage(message, "")
      WARNING -> logPlainMessage(message, " status='WARNING'")
      ERROR -> {
        val messageText = message.text.trim()
        val lineEnd = messageText.indexOf('\n')
        val firstLine: String
        val details: String
        if (lineEnd != -1) {
          firstLine = messageText.substring(0, lineEnd)
          details = " errorDetails='${escape(messageText.substring(lineEnd + 1))}'"
        }
        else {
          firstLine = messageText
          details = ""
        }
        printTeamCityMessage("message", "text='${escape(firstLine)}'$details status='ERROR'")
      }
      PROGRESS -> printTeamCityMessage("progressMessage", "'${escape(message.text)}'")
      BLOCK_STARTED -> delayedBlockStartMessages.addLast(message)
      BLOCK_FINISHED -> {
        if (!dropDelayedBlockStartMessageIfSame(message)) {
          printTeamCityMessage("blockClosed", "name='${escape(message.text)}'")
        }
      }
      ARTIFACT_BUILT -> printTeamCityMessage("publishArtifacts", "'${escape(message.text)}'")
      BUILD_STATUS -> printTeamCityMessage("buildStatus", "text='${escape(message.text)}'")
      STATISTICS -> {
        val index = message.text.indexOf('=')
        val key = escape(message.text.substring(0, index))
        val value = escape(message.text.substring(index + 1))
        printTeamCityMessage("buildStatisticValue", "key='$key' value='$value'")
      }
      SET_PARAMETER -> {
        val index = message.text.indexOf('=')
        val name = escape(message.text.substring(0, index))
        val value = escape(message.text.substring(index + 1))
        printTeamCityMessage("setParameter", "name='$name' value='$value'")
      }
      COMPILATION_ERRORS -> {
        val compiler = escape((message as CompilationErrorsLogMessage).compilerName)
        printTeamCityMessage("compilationStarted", "compiler='$compiler']")
        message.errorMessages.forEach {
          val messageText = escape(it)
          printTeamCityMessage("message", "text='$messageText' status='ERROR']")
        }
        printTeamCityMessage("compilationFinished", "compiler='$compiler']")
      }
      DEBUG -> {} //debug messages are printed to a separate file available in the build artifacts
    }
  }

  private fun logPlainMessage(message: LogMessage, status: String) {
    printDelayedBlockStartMessages()
    if (!status.isEmpty()) {
      printTeamCityMessage("message", "text='${escape(message.text)}'$status")
    }
    else {
      printMessageText(message.text)
    }
  }

  private fun printTeamCityMessage(messageId: String, messageArguments: String) {
    printDelayedBlockStartMessages()
    doPrintTeamCityMessage(messageId, messageArguments)
  }


  private fun printDelayedBlockStartMessages() {
    var message = delayedBlockStartMessages.pollFirst()
    while (message != null) {
      doPrintTeamCityMessage("blockOpened", "name='${escape(message.text)}'")
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
