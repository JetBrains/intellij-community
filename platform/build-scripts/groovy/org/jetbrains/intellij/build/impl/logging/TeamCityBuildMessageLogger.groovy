// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.logging

import com.intellij.util.SystemProperties
import groovy.transform.CompileStatic
import org.apache.tools.ant.Project
import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.LogMessage
import org.jetbrains.intellij.build.impl.BuildUtils

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.BiFunction

@CompileStatic
class TeamCityBuildMessageLogger extends BuildMessageLogger {
  public static final BiFunction<String, AntTaskLogger, BuildMessageLogger> FACTORY = { String taskName, AntTaskLogger antLogger ->
    new TeamCityBuildMessageLogger(taskName, antLogger)
  } as BiFunction<String, AntTaskLogger, BuildMessageLogger>
  private static final String ANT_OUTPUT_PREFIX = "###<<<>>>###:" //copied from jetbrains.buildServer.agent.ant.ServiceMessageBuildProgressLogger
  private static final PrintStream out = BuildUtils.realSystemOut
  private final String parallelTaskId
  private AntTaskLogger antTaskLogger
  private boolean isTeamCityListenerRegistered
  private final ConcurrentLinkedDeque<LogMessage> delayedBlockStartMessages = new ConcurrentLinkedDeque<>()

  TeamCityBuildMessageLogger(String parallelTaskId, AntTaskLogger antTaskLogger) {
    this.parallelTaskId = parallelTaskId
    this.antTaskLogger = antTaskLogger
    //if Ant script is started directly by TeamCity Ant runner its BuildListener will be present in antProject;
    //if Ant script is started from a forked Java task we cannot detect this automatically so explicit property should be passed
    isTeamCityListenerRegistered = antTaskLogger.antProject.buildListeners.any { it.class.name.startsWith("jetbrains.buildServer.") } ||
                                   SystemProperties.getBooleanProperty("intellij.build.teamcity.build.listener.available", false)
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
        printTeamCityMessage("message", true, "text='${escape(firstLine)}'$details status='ERROR'")
        break
      case LogMessage.Kind.PROGRESS:
        printTeamCityMessage("progressMessage", false, "'${escape(message.text)}'")
        break
      case LogMessage.Kind.BLOCK_STARTED:
        delayedBlockStartMessages.addLast(message)
        break
      case LogMessage.Kind.BLOCK_FINISHED:
        if (!dropDelayedBlockStartMessageIfSame(message)) {
          printTeamCityMessage("blockClosed", true, "name='${escape(message.text)}'")
        }
        break
      case LogMessage.Kind.ARTIFACT_BUILT:
        printTeamCityMessage("publishArtifacts", false, "'${escape(message.text)}'")
        break
      case LogMessage.Kind.BUILD_STATUS:
        printTeamCityMessage("buildStatus", false, "text='${escape(message.text)}'")
        break
      case LogMessage.Kind.STATISTICS:
        int index = message.text.indexOf('=')
        String key = escape(message.text.substring(0, index))
        String value = escape(message.text.substring(index + 1))
        printTeamCityMessage("buildStatisticValue", false, "key='$key' value='$value'")
        break
      case LogMessage.Kind.SET_PARAMETER:
        int index = message.text.indexOf('=')
        String name = escape(message.text.substring(0, index))
        String value = escape(message.text.substring(index + 1))
        printTeamCityMessage("setParameter", false, "name='$name' value='$value'")
        break
      case LogMessage.Kind.COMPILATION_ERRORS:
        String compiler = escape((message as CompilationErrorsLogMessage).compilerName)
        printTeamCityMessage("compilationStarted", false, "compiler='$compiler']")
        (message as CompilationErrorsLogMessage).errorMessages.each {
          String messageText = escape(it)
          printTeamCityMessage("message", false, "text='$messageText' status='ERROR']")
        }
        printTeamCityMessage("compilationFinished", false, "compiler='$compiler']")
        break
      case LogMessage.Kind.DEBUG:
        //debug messages are printed to a separate file available in the build artifacts
        break
    }
  }

  void logPlainMessage(LogMessage message, String status) {
    printDelayedBlockStartMessages()
    if (parallelTaskId != null || !status.isEmpty()) {
      printTeamCityMessage("message", true, "text='${escape(message.text)}'$status")
    }
    else {
      printMessageText(message.text)
    }
  }

  private void printTeamCityMessage(String messageId, boolean includeFlowId, String messageArguments) {
    printDelayedBlockStartMessages()
    doPrintTeamCityMessage(messageId, includeFlowId, messageArguments)
  }

  private void doPrintTeamCityMessage(String messageId, boolean includeFlowId, String messageArguments) {
    String flowArg = includeFlowId && parallelTaskId != null ? " flowId='${escape(parallelTaskId)}'" : ""
    String message = "##teamcity[$messageId$flowArg $messageArguments]"
    printMessageText(message)
  }

  private void printDelayedBlockStartMessages() {
    LogMessage message
    while ((message = delayedBlockStartMessages.pollFirst()) != null) {
      doPrintTeamCityMessage("blockOpened", true, "name='${escape(message.text)}'")
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

  private void printMessageText(String message) {
    if (BuildUtils.isUnderJpsBootstrap()) {
      // under jps-bootstrap we're logging directly to teamcity
      // so special prefixes are not required
      out.println(message)
      return
    }

    if (isTeamCityListenerRegistered) {
      //notify TeamCity via its BuildListener
      antTaskLogger.logMessageToOtherLoggers(message, Project.MSG_INFO)
      out.println(message)
    }
    else if (message.contains(ANT_OUTPUT_PREFIX)) {
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
