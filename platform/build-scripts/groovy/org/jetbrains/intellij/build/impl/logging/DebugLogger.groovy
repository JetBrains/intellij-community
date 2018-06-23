// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.logging

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessageLogger

/**
 * Used to print debug-level log message to a file in the build output. It firstly print messages to a temp file and copies it to the real
 * file after the build process cleans up the output directory.
 */
@CompileStatic
class DebugLogger {
  private final File tempFile
  private PrintWriter output
  private List<PrintWriterBuildMessageLogger> loggers = []

  DebugLogger() {
    tempFile = File.createTempFile("intellij-build", ".log")
    output = new PrintWriter(new BufferedWriter(new FileWriter(tempFile)))
  }

  synchronized void setOutputFile(File outputFile) {
    output.close()
    FileUtil.createParentDirs(outputFile)
    FileUtil.rename(tempFile, outputFile)
    output = new PrintWriter(new BufferedWriter(new FileWriter(outputFile, true)), true)
    loggers*.setOutput(output)
  }

  synchronized BuildMessageLogger createLogger(String taskName) {
    def logger = new PrintWriterBuildMessageLogger(output, taskName, { loggers.remove(it) })
    loggers << logger
    return logger
  }
}
