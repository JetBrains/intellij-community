// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.logging

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.CompilationErrorsLogMessage
import org.jetbrains.intellij.build.LogMessage
import org.jetbrains.intellij.build.impl.BuildUtils

import java.util.function.BiFunction
/**
 * @author nik
 */
@CompileStatic
class ConsoleBuildMessageLogger extends BuildMessageLogger {
  public static final BiFunction<String, AntTaskLogger, BuildMessageLogger> FACTORY = { String taskName, AntTaskLogger logger ->
    new ConsoleBuildMessageLogger(taskName)
  } as BiFunction<String, AntTaskLogger, BuildMessageLogger>
  private final String parallelTaskId
  private int indent
  private static final PrintStream out = BuildUtils.realSystemOut

  ConsoleBuildMessageLogger(String parallelTaskId) {
    this.parallelTaskId = parallelTaskId
  }

  @Override
  void processMessage(LogMessage message) {
    switch (message.kind) {
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
      case LogMessage.Kind.COMPILATION_ERRORS:
        String errorsString = (message as CompilationErrorsLogMessage).errorMessages.join("\n")
        printMessage("Compilation errors (${(message as CompilationErrorsLogMessage).compilerName}):\n$errorsString")
        break
      default:
        printMessage(message.text)
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
