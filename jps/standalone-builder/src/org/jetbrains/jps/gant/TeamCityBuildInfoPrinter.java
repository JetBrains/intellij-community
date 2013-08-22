/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.gant;

/**
 * @author nik
 */
public class TeamCityBuildInfoPrinter implements BuildInfoPrinter {
  private static char escapedChar(char c) {
    switch (c) {
      case '\n': return 'n';
      case '\r': return 'r';
      case '\u0085': return 'x'; // next-line character
      case '\u2028': return 'l'; // line-separator character
      case '\u2029': return 'p'; // paragraph-separator character
      case '|': return '|';
      case '\'': return '\'';
      case '[': return '[';
      case ']': return ']';
    }

    return 0;
  }

  private static String escape(String text) {
    StringBuilder escaped = new StringBuilder();
    for (char c: text.toCharArray()) {
      Character escChar = escapedChar(c);
      if (escChar == 0) {
        escaped.append(c);
      } else {
        escaped.append('|').append(escChar);
      }
    }

    return escaped.toString();
  }

  @Override
  public void printProgressMessage(JpsGantProjectBuilder builder, String message) {
    String escapedMessage = escape(message);
    builder.info("##teamcity[progressMessage '" + escapedMessage + "']");
  }

  @Override
  public void printBlockOpenedMessage(JpsGantProjectBuilder builder, String blockId) {
    builder.info("##teamcity[blockOpened name='" + escape(blockId) + "']");
  }

  @Override
  public void printBlockClosedMessage(JpsGantProjectBuilder builder, String blockId) {
    builder.info("##teamcity[blockClosed name='" + escape(blockId) + "']");
  }

  @Override
  public void printStatisticsMessage(JpsGantProjectBuilder builder, String key, String value) {
    builder.info("##teamcity[buildStatisticValue key='" + escape(key) + "' value='" + escape(value) + "']");
  }

  @Override
  public void printCompilationErrors(JpsGantProjectBuilder builder, String compilerName, String messages) {
    String escapedCompiler = escape(compilerName);
    String escapedOutput = escape(messages);
    builder.info("##teamcity[compilationStarted compiler='" + escapedCompiler + "']");
    builder.info("##teamcity[message text='" + escapedOutput + "' status='ERROR']");
    builder.info("##teamcity[compilationFinished compiler='" + escapedCompiler + "']");
  }

  @Override
  public void printCompilationStart(JpsGantProjectBuilder builder, String compilerName) {
    builder.info("##teamcity[compilationStarted compiler='" + escape(compilerName) + "']");
  }

  @Override
  public void printCompilationFinish(JpsGantProjectBuilder builder, String compilerName) {
    builder.info("##teamcity[compilationFinished compiler='" + escape(compilerName) + "']");
  }
}
