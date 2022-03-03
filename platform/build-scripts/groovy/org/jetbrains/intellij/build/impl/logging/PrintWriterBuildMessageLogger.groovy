// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.logging

import groovy.transform.CompileStatic

import java.util.function.Consumer

@CompileStatic
final class PrintWriterBuildMessageLogger extends BuildMessageLoggerBase {
  private BufferedWriter output
  private final Consumer<PrintWriterBuildMessageLogger> disposer

  PrintWriterBuildMessageLogger(BufferedWriter output, String parallelTaskId, Consumer<PrintWriterBuildMessageLogger> disposer) {
    super(parallelTaskId)
    this.disposer = disposer
    this.output = output
  }

  synchronized void setOutput(BufferedWriter output) {
    this.output = output
  }

  @Override
  protected synchronized void printLine(String line) {
    output.write(line)
    output.write('\n' as char)
    output.flush()
  }

  @Override
  void dispose() {
    disposer.accept(this)
  }
}
