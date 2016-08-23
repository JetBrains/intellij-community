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
 * @author nik
 */
@CompileStatic
class ConsoleBuildMessageLogger extends BuildMessageLogger {
  public static final Function<String, BuildMessageLogger> FACTORY = { String taskName -> new ConsoleBuildMessageLogger(taskName) } as Function<String, BuildMessageLogger>
  private final String parallelTaskId
  private int indent
  private static final PrintStream out = BuildUtils.realSystemOut

  ConsoleBuildMessageLogger(String parallelTaskId) {
    this.parallelTaskId = parallelTaskId
  }

  @Override
  void processMessage(LogMessage message) {
    switch (message.kind) {
      case LogMessage.Kind.ERROR:
      case LogMessage.Kind.WARNING:
      case LogMessage.Kind.INFO:
      case LogMessage.Kind.PROGRESS:
        printMessage(message.text)
        break
      case LogMessage.Kind.BLOCK_STARTED:
        printMessage(message.text)
        indent++
        break
      case LogMessage.Kind.BLOCK_FINISHED:
        indent--
        break
      case LogMessage.Kind.ARTIFACT_BUILT:
        printMessage("Artifact built: $message.text")
        break
    }
  }

  private printMessage(String message) {
    String taskPrefix = parallelTaskId == null ? "" : "[$parallelTaskId] "
    message.eachLine {
      out.println(" " * indent + taskPrefix + it)
    }
  }
}
