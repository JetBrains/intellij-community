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

import org.jetbrains.intellij.build.BuildMessageLogger

import java.util.function.Function

/**
 * @author nik
 */
class ConsoleBuildMessageLogger extends BuildMessageLogger {
  public static final Function<String, BuildMessageLogger> FACTORY = { new ConsoleBuildMessageLogger(it) }
  private final String parallelTaskId
  private int indent
  private static final PrintStream out = System.out //we need to store real System.out because AntBuilder replaces it during task execution

  ConsoleBuildMessageLogger(String parallelTaskId) {
    this.parallelTaskId = parallelTaskId
  }

  @Override
  void logMessage(String message, Level level) {
    printMessage(message)
  }

  @Override
  void logProgressMessage(String message) {
    printMessage(message)
  }

  private printMessage(String message) {
    String taskPrefix = parallelTaskId == null ? "" : "[$parallelTaskId] "
    message.eachLine {
      out.println(" " * indent + taskPrefix + it)
    }
  }

  @Override
  void startBlock(String blockName) {
    printMessage(blockName)
    indent++
  }

  @Override
  void finishBlock(String blockName) {
    indent--
  }
}
