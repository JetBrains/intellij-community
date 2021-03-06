// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.io.File
import java.io.InputStream
import java.lang.System.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Executes a Java class in a forked JVM.
 */
@JvmOverloads
fun runJava(mainClass: String,
            args: Iterable<String>,
            jvmArgs: Iterable<String>,
            classPath: Iterable<String>,
            logger: Logger = System.getLogger(mainClass),
            timeoutMillis: Long = Timeout.DEFAULT) {
  val timeout = Timeout(timeoutMillis)
  var errorReader: Thread? = null
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

    errorReader = readErrorOutput(process, timeout, logger)
    readOutputAndBlock(process, timeout, logger)

    fun javaRunFailed(reason: String) {
      // do not throw error, but log as error to reduce bloody groovy stacktrace
      logger.debug { "classPath=${classPathStringBuilder.substring("-classpath".length)})" }
      logger.log(Logger.Level.ERROR, null as ResourceBundle?,
                 "Cannot execute $mainClass (args=$args, vmOptions=$jvmArgs): $reason")
    }

    if (!process.waitFor(timeout.remainingTime, TimeUnit.MILLISECONDS)) {
      try {
        dumpThreads(process.pid())
      }
      catch (e: Exception) {
        logger.warn("Cannot dump threads: ${e.message}")
      }
      process.destroyForcibly().waitFor()
      javaRunFailed("$timeout timeout")
    }
    val exitCode = process.exitValue()
    if (exitCode != 0) {
      javaRunFailed("exitCode=${process.exitValue()}")
    }
  }
  finally {
    Files.deleteIfExists(classpathFile)
    errorReader?.join()
  }
}

@JvmOverloads
fun runProcess(vararg args: String, workingDir: Path? = null,
               logger: Logger = System.getLogger(args.joinToString(separator = " ")),
               timeoutMillis: Long = Timeout.DEFAULT) {
  runProcess(args.toList(), workingDir, logger, timeoutMillis)
}

@JvmOverloads
fun runProcess(args: List<String>, workingDir: Path? = null,
               logger: Logger = System.getLogger(args.joinToString(separator = " ")),
               timeoutMillis: Long = Timeout.DEFAULT) {
  logger.debug { "Execute: $args" }
  val timeout = Timeout(timeoutMillis)
  var errorReader: Thread? = null
  try {
    val process = ProcessBuilder(args).directory(workingDir?.toFile()).start()

    errorReader = readErrorOutput(process, timeout, logger)
    readOutputAndBlock(process, timeout, logger)

    if (!process.waitFor(timeout.remainingTime, TimeUnit.MILLISECONDS)) {
      process.destroyForcibly().waitFor()
      throw ProcessRunTimedOut("Cannot execute $args: $timeout timeout")
    }
    val exitCode = process.exitValue()
    if (exitCode != 0) {
      throw RuntimeException("Cannot execute $args (exitCode=$exitCode)")
    }
  }
  finally {
    errorReader?.join()
  }
}

private fun readOutputAndBlock(process: Process, timeout: Timeout, logger: Logger) {
  process.inputStream.consume(process, timeout, logger::info)
}

internal const val errorOutputReaderNamePrefix = "error-output-reader-of-pid-"
private fun readErrorOutput(process: Process, timeout: Timeout, logger: Logger) =
  thread(name = "$errorOutputReaderNamePrefix${process.pid()}") {
    process.errorStream.consume(process, timeout, logger::warn)
  }

private fun InputStream.consume(process: Process, timeout: Timeout, consume: (String) -> Unit) {
  bufferedReader().use { reader ->
    var linesCount = 0
    var linesBuffer = StringBuilder()
    while (!timeout.isElapsed && (process.isAlive || reader.ready())) {
      if (reader.ready()) {
        val char = reader.read().takeIf { it != -1 }?.toChar()
        if (char == '\n' || char == '\r') linesCount++
        if (char != null) linesBuffer.append(char)
        if (char == null || !reader.ready() || linesCount > 100) {
          consume(linesBuffer.toString())
          linesBuffer = StringBuilder()
          linesCount = 0
        }
      }
      else {
        Thread.sleep(100L)
      }
    }
  }
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

internal class ProcessRunTimedOut(msg: String) : RuntimeException(msg)

internal class Timeout(private val millis: Long) {
  companion object {
    val DEFAULT = TimeUnit.MINUTES.toMillis(10L)
  }

  private val start = System.currentTimeMillis()

  val remainingTime: Long
    get() = start.plus(millis)
              .minus(System.currentTimeMillis())
              .takeIf { it > 0 } ?: 0
  val isElapsed: Boolean get() = remainingTime == 0L

  override fun toString() = "${millis}ms"
}

internal fun dumpThreads(pid: Long) {
  val jstack = System.getenv("JAVA_HOME")
                 ?.removeSuffix("/")
                 ?.removeSuffix("\\")
                 ?.let { "$it/bin/jstack" }
               ?: "jstack"
  runProcess(jstack, "$pid")
}