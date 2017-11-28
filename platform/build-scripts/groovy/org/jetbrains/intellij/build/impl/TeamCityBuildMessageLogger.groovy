/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.apache.tools.ant.Project
import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.CompilationErrorsLogMessage
import org.jetbrains.intellij.build.LogMessage

import java.util.function.BiFunction
/**
 * todo[nik] this is replacement for BuildInfoPrinter. BuildInfoPrinter should be deleted after we move its remaining methods to this class.
 *
 * @author nik
 */
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

  TeamCityBuildMessageLogger(String parallelTaskId, AntTaskLogger antTaskLogger) {
    this.parallelTaskId = parallelTaskId
    this.antTaskLogger = antTaskLogger
    isTeamCityListenerRegistered = antTaskLogger.antProject.buildListeners.any { it.class.name.startsWith("jetbrains.buildServer.") }
  }

  @Override
  void processMessage(LogMessage message) {
    switch (message.kind) {
      case LogMessage.Kind.ERROR:
      case LogMessage.Kind.WARNING:
      case LogMessage.Kind.INFO:
        logPlainMessage(message)
        break
      case LogMessage.Kind.PROGRESS:
        printTeamCityMessage("progressMessage", false, "'${escape(message.text)}'")
        break
      case LogMessage.Kind.BLOCK_STARTED:
        printTeamCityMessage("blockOpened", true, "name='${escape(message.text)}'")
        break
      case LogMessage.Kind.BLOCK_FINISHED:
        printTeamCityMessage("blockClosed", true, "name='${escape(message.text)}'")
        break
      case LogMessage.Kind.ARTIFACT_BUILT:
        printTeamCityMessage("publishArtifacts", false, "'${escape(message.text)}'")
        break
      case LogMessage.Kind.STATISTICS:
        int index = message.text.indexOf('=')
        String key = escape(message.text.substring(0, index))
        String value = escape(message.text.substring(index + 1))
        printTeamCityMessage("buildStatisticValue", false, "key='$key' value='$value'")
        break
      case LogMessage.Kind.COMPILATION_ERROR:
        int index = message.text.indexOf(':')
        String compiler = escape(message.text.substring(0, index))
        String messageText = escape(message.text.substring(index + 1))
        printTeamCityMessage("compilationStarted", false, "compiler='$compiler']");
        printTeamCityMessage("message", false, "text='$messageText' status='ERROR']");
        printTeamCityMessage("compilationFinished", false, "compiler='$compiler']");
        break
      case LogMessage.Kind.COMPILATION_ERRORS:
        String compiler = escape((message as CompilationErrorsLogMessage).compilerName)
        printTeamCityMessage("compilationStarted", false, "compiler='$compiler']");
        (message as CompilationErrorsLogMessage).errorMessages.each {
          String messageText = escape(it)
          printTeamCityMessage("message", false, "text='$messageText' status='ERROR']");
        }
        printTeamCityMessage("compilationFinished", false, "compiler='$compiler']");
        break
    }
  }

  void logPlainMessage(LogMessage message) {
    String status = message.kind == LogMessage.Kind.WARNING ? " status='WARNING'" : ""
    if (parallelTaskId != null || !status.isEmpty()) {
      printTeamCityMessage("message", true, "text='${escape(message.text)}'$status")
    }
    else {
      printMessageText(message.text)
    }
  }

  private void printTeamCityMessage(String messageId, boolean includeFlowId, String messageArguments) {
    String flowArg = includeFlowId && parallelTaskId != null ? " flowId='${escape(parallelTaskId)}'" : ""
    String message = "##teamcity[$messageId$flowArg $messageArguments]"
    printMessageText(message)
  }

  private void printMessageText(String message) {
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

    return 0;
  }

  private static String escape(String text) {
    StringBuilder escaped = new StringBuilder();
    for (char c : text.toCharArray()) {
      char escChar = escapeChar(c);
      if (escChar == 0 as char) {
        escaped.append(c);
      }
      else {
        escaped.append('|').append(escChar);
      }
    }

    return escaped.toString();
  }
}
