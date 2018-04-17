// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.logging

import groovy.transform.CompileStatic

import java.util.function.Consumer

@CompileStatic
class PrintWriterBuildMessageLogger extends BuildMessageLoggerBase {
  private PrintWriter output
  private final Consumer<PrintWriterBuildMessageLogger> disposer

  PrintWriterBuildMessageLogger(PrintWriter output, String parallelTaskId, Consumer<PrintWriterBuildMessageLogger> disposer) {
    super(parallelTaskId)
    this.disposer = disposer
    this.output = output
  }

  synchronized void setOutput(PrintWriter output) {
    this.output = output
  }

  @Override
  protected synchronized void printLine(String line) {
    output.println(line)
  }

  @Override
  void dispose() {
    disposer.accept(this)
  }
}
