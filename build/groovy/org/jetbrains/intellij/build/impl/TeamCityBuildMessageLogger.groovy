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
import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.LogMessage

import java.util.function.Function

/**
 * todo[nik] this is replacement for BuildInfoPrinter. BuildInfoPrinter should be deleted after we move its remaining methods to this class.
 *
 * @author nik
 */
@CompileStatic
class TeamCityBuildMessageLogger extends BuildMessageLogger {
  public static final Function<String, BuildMessageLogger> FACTORY = { String taskName -> new TeamCityBuildMessageLogger(taskName) } as Function<String, BuildMessageLogger>
  private static final PrintStream out = BuildUtils.realSystemOut
  private final String parallelTaskId

  TeamCityBuildMessageLogger(String parallelTaskId) {
    this.parallelTaskId = parallelTaskId
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
        out.println "##teamcity[progressMessage '${escape(message.text)}']"
        break
      case LogMessage.Kind.BLOCK_STARTED:
        printTeamCityMessage("blockOpened", "name='${escape(message.text)}'")
        break
      case LogMessage.Kind.BLOCK_FINISHED:
        printTeamCityMessage("blockClosed", "name='${escape(message.text)}'")
        break
    }
  }

  void logPlainMessage(LogMessage message) {
    String status = message.kind == LogMessage.Kind.WARNING ? " status='WARNING'" : ""
    if (parallelTaskId != null || !status.isEmpty()) {
      printTeamCityMessage("message", "text='${escape(message.text)}'$status")
    }
    else {
      out.println message.text
    }
  }

  private void printTeamCityMessage(String messageId, String messageArguments) {
    String flowArg = parallelTaskId != null ? " flowId='${escape(parallelTaskId)}'" : ""
    out.println "##teamcity[$messageId$flowArg $messageArguments]"
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
