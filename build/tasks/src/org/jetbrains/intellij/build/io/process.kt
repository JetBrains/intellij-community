// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.io.File
import java.lang.System.Logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * Executes a Java class in a forked JVM.
 */
fun runJava(mainClass: String,
            args: Iterable<String>,
            jvmArgs: Iterable<String>,
            classPath: Iterable<String>,
            logger: Logger) {
  val classpathFile = Files.createTempFile("classpath-", ".txt")
  try {
    val classPathStringBuilder = StringBuilder()
    classPathStringBuilder.append("-classpath").append('\n')
    for (s in classPath) {
      appendArg(s, classPathStringBuilder)
      classPathStringBuilder.append(File.pathSeparator)
    }
    classPathStringBuilder.setLength(classPathStringBuilder.length - 1)
    Files.writeString(classpathFile, classPathStringBuilder)

    val processArgs = mutableListOf<String>()
    processArgs.add(ProcessHandle.current().info().command().orElseThrow())
    processArgs.add("-ea")
    @Suppress("SpellCheckingInspection")
    processArgs.add("-Djava.awt.headless=true")
    processArgs.addAll(jvmArgs)
    processArgs.add("@$classpathFile")
    processArgs.add(mainClass)
    processArgs.addAll(args)

    logger.debug { "Execute: $processArgs" }

    val process = ProcessBuilder(processArgs).start()

    readErrorOutput(process, logger)
    readOutputAndBlock(process, logger)

    val exitCode = process.waitFor()
    if (exitCode != 0) {
      throw RuntimeException("Cannot execute $mainClass (exitCode=$exitCode, args=$args, vmOptions=$jvmArgs, " +
                             "classPath=${classPathStringBuilder.substring("-classpath".length)})")
    }
  }
  finally {
    Files.deleteIfExists(classpathFile)
  }
}

fun runProcess(args: List<String>, workingDir: Path?, logger: Logger) {
  logger.debug { "Execute: $args" }
  val process = ProcessBuilder(args).directory(workingDir?.toFile()).start()

  readErrorOutput(process, logger)
  readOutputAndBlock(process, logger)

  val exitCode = process.waitFor()
  if (exitCode != 0) {
    throw RuntimeException("Cannot execute $args (exitCode=$exitCode)")
  }
}

private fun readOutputAndBlock(process: Process, logger: Logger) {
  process.inputStream.bufferedReader().use { reader ->
    while (true) {
      val line = reader.readLine() ?: break
      logger.info(line)
    }
  }
}

private fun readErrorOutput(process: Process, logger: Logger) {
  Thread {
    process.errorStream.bufferedReader().use { reader ->
      while (true) {
        val line = reader.readLine() ?: break
        logger.warn(line)
      }
    }
  }.start()
}

private fun appendArg(value: String, builder: StringBuilder) {
  if (!value.any(" #'\"\n\r\t"::contains)) {
    builder.append(value)
    return
  }

  for (c in value) {
    when (c) {
      ' ', '#', '\'' -> builder.append('"').append(c).append('"')
      '"' -> builder.append("\"\\\"\"")
      '\n' -> builder.append("\"\\n\"")
      '\r' -> builder.append("\"\\r\"")
      '\t' -> builder.append("\"\\t\"")
      else -> builder.append(c)
    }
  }
}