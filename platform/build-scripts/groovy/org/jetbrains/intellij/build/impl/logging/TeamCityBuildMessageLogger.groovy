// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging

import groovy.transform.CompileStatic
import kotlin.jvm.functions.Function0
import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.CompilationErrorsLogMessage
import org.jetbrains.intellij.build.LogMessage
import org.jetbrains.intellij.build.impl.BuildUtils

import java.util.concurrent.ConcurrentLinkedDeque

@CompileStatic
final class TeamCityBuildMessageLogger extends BuildMessageLogger {
  public static final Function0<BuildMessageLogger> FACTORY = new Function0<BuildMessageLogger>() {
    @Override
    BuildMessageLogger invoke() {
      return new TeamCityBuildMessageLogger()
    }
  }
  private static final String ANT_OUTPUT_PREFIX = "###<<<>>>###:" //copied from jetbrains.buildServer.agent.ant.ServiceMessageBuildProgressLogger
  private static final PrintStream out = System.out
  private final ConcurrentLinkedDeque<LogMessage> delayedBlockStartMessages = new ConcurrentLinkedDeque<>()

  TeamCityBuildMessageLogger() {
  }

  @Override
  void processMessage(LogMessage message) {
    switch (message.kind) {
      case LogMessage.Kind.INFO:
        logPlainMessage(message, "")
        break
      case LogMessage.Kind.WARNING:
        logPlainMessage(message, " status='WARNING'")
        break
      case LogMessage.Kind.ERROR:
        def messageText = message.text.trim()
        int lineEnd = messageText.indexOf('\n')
        String firstLine
        String details
        if (lineEnd != -1) {
          firstLine = messageText.substring(0, lineEnd)
          details = " errorDetails='${escape(messageText.substring(lineEnd + 1))}'"
        }
        else {
          firstLine = messageText
          details = ""
        }
        printTeamCityMessage("message", "text='${escape(firstLine)}'$details status='ERROR'")
        break
      case LogMessage.Kind.PROGRESS:
        printTeamCityMessage("progressMessage", "'${escape(message.text)}'")
        break
      case LogMessage.Kind.BLOCK_STARTED:
        delayedBlockStartMessages.addLast(message)
        break
      case LogMessage.Kind.BLOCK_FINISHED:
        if (!dropDelayedBlockStartMessageIfSame(message)) {
          printTeamCityMessage("blockClosed", "name='${escape(message.text)}'")
        }
        break
      case LogMessage.Kind.ARTIFACT_BUILT:
        printTeamCityMessage("publishArtifacts", "'${escape(message.text)}'")
        break
      case LogMessage.Kind.BUILD_STATUS:
        printTeamCityMessage("buildStatus", "text='${escape(message.text)}'")
        break
      case LogMessage.Kind.STATISTICS:
        int index = message.text.indexOf('=')
        String key = escape(message.text.substring(0, index))
        String value = escape(message.text.substring(index + 1))
        printTeamCityMessage("buildStatisticValue", "key='$key' value='$value'")
        break
      case LogMessage.Kind.SET_PARAMETER:
        int index = message.text.indexOf('=')
        String name = escape(message.text.substring(0, index))
        String value = escape(message.text.substring(index + 1))
        printTeamCityMessage("setParameter", "name='$name' value='$value'")
        break
      case LogMessage.Kind.COMPILATION_ERRORS:
        String compiler = escape((message as CompilationErrorsLogMessage).compilerName)
        printTeamCityMessage("compilationStarted", "compiler='$compiler']")
        (message as CompilationErrorsLogMessage).errorMessages.each {
          String messageText = escape(it)
          printTeamCityMessage("message", "text='$messageText' status='ERROR']")
        }
        printTeamCityMessage("compilationFinished", "compiler='$compiler']")
        break
      case LogMessage.Kind.DEBUG:
        //debug messages are printed to a separate file available in the build artifacts
        break
    }
  }

  void logPlainMessage(LogMessage message, String status) {
    printDelayedBlockStartMessages()
    if (!status.isEmpty()) {
      printTeamCityMessage("message", "text='${escape(message.text)}'$status")
    }
    else {
      printMessageText(message.text)
    }
  }

  private void printTeamCityMessage(String messageId, String messageArguments) {
    printDelayedBlockStartMessages()
    doPrintTeamCityMessage(messageId, messageArguments)
  }

  private static void doPrintTeamCityMessage(String messageId, String messageArguments) {
    printMessageText("##teamcity[$messageId $messageArguments]")
  }

  private void printDelayedBlockStartMessages() {
    LogMessage message
    while ((message = delayedBlockStartMessages.pollFirst()) != null) {
      doPrintTeamCityMessage("blockOpened", "name='${escape(message.text)}'")
    }
  }

  private boolean dropDelayedBlockStartMessageIfSame(LogMessage message) {
    LogMessage last = delayedBlockStartMessages.peekLast()
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

  private static void printMessageText(String message) {
    if (BuildUtils.INSTANCE.isUnderJpsBootstrap()) {
      // under jps-bootstrap we're logging directly to teamcity
      // so special prefixes are not required
      out.println(message)
      return
    }

    if (message.contains(ANT_OUTPUT_PREFIX)) {
      out.println(message)
    }
    else {
      //TeamCity will ignore an output line if it doesn't contain the prefix so we need to add it manually
      out.println("$ANT_OUTPUT_PREFIX$message")
    }
  }

  private static char escapeChar(char c) {
    switch (c) {
      case '\n': return 'n' as char
      case '\r': return 'r' as char
      case '\u0085': return 'x' as char // next-line character
      case '\u2028': return 'l' as char // line-separator character
      case '\u2029': return 'p' as char // paragraph-separator character
      case '|': return '|' as char
      case '\'': return '\'' as char
      case '[': return '[' as char
      case ']': return ']' as char
    }

    return 0
  }

  private static String escape(String text) {
    StringBuilder escaped = new StringBuilder()
    for (char c : text.toCharArray()) {
      char escChar = escapeChar(c)
      if (escChar == 0 as char) {
        escaped.append(c)
      }
      else {
        escaped.append('|').append(escChar)
      }
    }

    return escaped.toString()
  }
}
