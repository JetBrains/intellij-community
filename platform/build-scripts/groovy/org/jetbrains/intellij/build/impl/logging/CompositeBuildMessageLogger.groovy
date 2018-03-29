// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.logging

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.LogMessage

@CompileStatic
class CompositeBuildMessageLogger extends BuildMessageLogger {
  private List<BuildMessageLogger> loggers

  CompositeBuildMessageLogger(List<BuildMessageLogger> loggers) {
    this.loggers = loggers
  }

  @Override
  void processMessage(LogMessage message) {
    loggers*.processMessage(message)
  }
}
