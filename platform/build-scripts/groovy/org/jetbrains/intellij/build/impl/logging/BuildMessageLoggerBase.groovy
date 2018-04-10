// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.logging

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.CompilationErrorsLogMessage
import org.jetbrains.intellij.build.LogMessage

@CompileStatic
abstract class BuildMessageLoggerBase extends BuildMessageLogger {
  private int indent
  private final String parallelTaskId

  BuildMessageLoggerBase(String parallelTaskId) {
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
        if (shouldBePrinted(message.kind)) {
          printMessage(message.text)
        }
        break
    }
  }

  protected boolean shouldBePrinted(LogMessage.Kind kind) {
    true
  }

  private printMessage(String message) {
    String taskPrefix = parallelTaskId == null ? "" : "[$parallelTaskId] "
    message.eachLine {
      def line = " " * indent + taskPrefix + it
      printLine(line)
    }
  }

  protected abstract void printLine(String line)
}
