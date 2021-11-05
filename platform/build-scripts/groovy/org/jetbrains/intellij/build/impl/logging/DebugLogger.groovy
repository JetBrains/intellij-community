// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.logging

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessageLogger

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Used to print debug-level log message to a file in the build output. It firstly print messages to a temp file and copies it to the real
 * file after the build process cleans up the output directory.
 */
@CompileStatic
final class DebugLogger {
  private final Path tempFile
  private BufferedWriter output
  private Path outputFile
  private final List<PrintWriterBuildMessageLogger> loggers = new ArrayList<>()

  DebugLogger() {
    tempFile = Files.createTempFile("intellij-build", ".log")
    Files.createDirectories(tempFile.parent)
    output = Files.newBufferedWriter(tempFile)
  }

  synchronized void setOutputFile(Path outputFile) {
    this.outputFile = outputFile
    output.close()
    Files.createDirectories(outputFile.parent)
    if (Files.exists(tempFile)) {
      Files.move(tempFile, outputFile, StandardCopyOption.REPLACE_EXISTING)
    }
    output = Files.newBufferedWriter(outputFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    loggers*.setOutput(output)
  }

  synchronized Path getOutputFile() {
    return outputFile
  }

  synchronized BuildMessageLogger createLogger(String taskName) {
    PrintWriterBuildMessageLogger logger = new PrintWriterBuildMessageLogger(output, taskName, { loggers.remove(it) })
    loggers.add(logger)
    return logger
  }
}
