// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.LogMessage

@CompileStatic
final class CompositeBuildMessageLogger extends BuildMessageLogger {
  private List<BuildMessageLogger> loggers

  CompositeBuildMessageLogger(List<BuildMessageLogger> loggers) {
    this.loggers = loggers
  }

  @Override
  void processMessage(LogMessage message) {
    loggers*.processMessage(message)
  }

  @Override
  void dispose() {
    loggers*.dispose()
  }
}
