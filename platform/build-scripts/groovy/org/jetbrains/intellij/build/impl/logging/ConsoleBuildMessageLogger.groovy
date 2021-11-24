// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.logging

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.LogMessage
import org.jetbrains.intellij.build.impl.BuildUtils

import java.util.function.BiFunction

@CompileStatic
final class ConsoleBuildMessageLogger extends BuildMessageLoggerBase {
  public static final BiFunction<String, AntTaskLogger, BuildMessageLogger> FACTORY = { String taskName, AntTaskLogger logger ->
    new ConsoleBuildMessageLogger(taskName)
  } as BiFunction<String, AntTaskLogger, BuildMessageLogger>
  private static final PrintStream out = BuildUtils.realSystemOut

  ConsoleBuildMessageLogger(String parallelTaskId) {
    super(parallelTaskId)
  }

  @Override
  void processMessage(LogMessage message) {
    // reported by trace exporter
    if (message.kind != LogMessage.Kind.BLOCK_STARTED && message.kind != LogMessage.Kind.BLOCK_FINISHED) {
      super.processMessage(message)
    }
  }

  @Override
  protected boolean shouldBePrinted(LogMessage.Kind kind) {
    return kind != LogMessage.Kind.DEBUG
  }

  @Override
  protected void printLine(String line) {
    out.println(line)
  }
}
