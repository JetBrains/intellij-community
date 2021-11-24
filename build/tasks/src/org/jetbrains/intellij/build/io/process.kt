// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import org.jetbrains.intellij.build.tasks.tracer
import org.jetbrains.intellij.build.tasks.use
import java.io.File
import java.io.InputStream
import java.lang.System.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Executes a Java class in a forked JVM.
 */
fun runJava(mainClass: String,
            args: Iterable<String>,
            jvmArgs: Iterable<String>,
            classPath: Iterable<String>,
            logger: Logger = System.getLogger(mainClass),
            timeoutMillis: Long = Timeout.DEFAULT,
            workingDir: Path? = null) {
  val timeout = Timeout(timeoutMillis)
  var errorReader: CompletableFuture<Void>? = null
  val classpathFile = Files.createTempFile("classpath-", ".txt")
  tracer.spanBuilder("runJava")
    .setAttribute("mainClass", mainClass)
    .setAttribute(AttributeKey.stringArrayKey("args"), args.toList())
    .setAttribute(AttributeKey.stringArrayKey("jvmArgs"), jvmArgs.toList())
    .setAttribute("workingDir", workingDir?.toString() ?: "")
    .setAttribute("timeoutMillis", timeoutMillis)
    .startSpan()
    .use { span ->
      try {
        val classPathStringBuilder = createClassPathFile(classPath, classpathFile)
        val processArgs = createProcessArgs(jvmArgs = jvmArgs, classpathFile = classpathFile, mainClass = mainClass, args = args)
        span.setAttribute(AttributeKey.stringArrayKey("processArgs"), processArgs)
        val process = ProcessBuilder(processArgs).directory(workingDir?.toFile()).start()

        errorReader = readErrorOutput(process, timeout, logger)
        readOutputAndBlock(process, timeout, logger)

        fun javaRunFailed(reason: String) {
          // do not throw error, but log as error to reduce bloody groovy stacktrace
          logger.debug { "classPath=${classPathStringBuilder.substring("-classpath".length)})" }

          val message = "Cannot execute $mainClass (args=$args, vmOptions=$jvmArgs): $reason"
          span.setStatus(StatusCode.ERROR, message)
          logger.log(Logger.Level.ERROR, null as ResourceBundle?, message)
        }

        if (!process.waitFor(timeout.remainingTime, TimeUnit.MILLISECONDS)) {
          try {
            dumpThreads(process.pid())
          }
          catch (e: Exception) {
            span.addEvent("cannot dump threads: ${e.message}")
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
}

internal fun runJavaWithOutputToFile(mainClass: String,
                                     args: Iterable<String>,
                                     jvmArgs: Iterable<String>,
                                     classPath: Iterable<String>,
                                     timeoutMillis: Long = Timeout.DEFAULT,
                                     outputFile: Path,
                                     workingDir: Path? = null) {
  Files.createDirectories(outputFile.parent)

  val timeout = Timeout(timeoutMillis)
  val classpathFile = Files.createTempFile("classpath-", ".txt")
  tracer.spanBuilder("runJava")
    .setAttribute("mainClass", mainClass)
    .setAttribute(AttributeKey.stringArrayKey("args"), args.toList())
    .setAttribute(AttributeKey.stringArrayKey("jvmArgs"), jvmArgs.toList())
    .setAttribute("outputFile", outputFile.toString())
    .setAttribute("workingDir", workingDir?.toString() ?: "")
    .setAttribute("timeoutMillis", timeoutMillis)
    .startSpan()
    .use { span ->
      try {
        createClassPathFile(classPath, classpathFile)
        val processArgs = createProcessArgs(jvmArgs = jvmArgs, classpathFile = classpathFile, mainClass = mainClass, args = args)
        span.setAttribute(AttributeKey.stringArrayKey("processArgs"), processArgs)
        val process = ProcessBuilder(processArgs)
          .directory(workingDir?.toFile())
          .redirectErrorStream(true)
          .redirectOutput(outputFile.toFile())
          .start()

        fun javaRunFailed(reason: String) {
          val message = "Cannot execute $mainClass, see details in ${outputFile.fileName} (args=$args, vmOptions=$jvmArgs): $reason"
          span.setStatus(StatusCode.ERROR, message)
          if (Files.exists(outputFile)) {
            span.setAttribute("processOutput", Files.readString(outputFile))
          }
          throw RuntimeException(message)
        }

        if (!process.waitFor(timeout.remainingTime, TimeUnit.MILLISECONDS)) {
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
      }
    }
}

private fun createProcessArgs(jvmArgs: Iterable<String>,
                              classpathFile: Path?,
                              mainClass: String,
                              args: Iterable<String>): MutableList<String> {
  val processArgs = mutableListOf<String>()
  // FIXME: enforce JBR
  processArgs.add(ProcessHandle.current().info().command().orElseThrow())
  @Suppress("SpellCheckingInspection")
  processArgs.add("-Djava.awt.headless=true")
  processArgs.add("-Dapple.awt.UIElement=true")
  processArgs.addAll(jvmArgs)
  processArgs.add("@$classpathFile")
  processArgs.add(mainClass)
  processArgs.addAll(args)
  return processArgs
}

private fun createClassPathFile(classPath: Iterable<String>, classpathFile: Path): StringBuilder {
  val classPathStringBuilder = StringBuilder()
  classPathStringBuilder.append("-classpath").append('\n')
  for (s in classPath) {
    appendArg(s, classPathStringBuilder)
    classPathStringBuilder.append(File.pathSeparator)
  }
  classPathStringBuilder.setLength(classPathStringBuilder.length - 1)
  Files.writeString(classpathFile, classPathStringBuilder)
  return classPathStringBuilder
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
  tracer.spanBuilder("runProcess")
    .setAttribute(AttributeKey.stringArrayKey("args"), args)
    .setAttribute("timeoutMillis", timeoutMillis)
    .startSpan()
    .use {
      val timeout = Timeout(timeoutMillis)
      val process = ProcessBuilder(args).directory(workingDir?.toFile()).start()
      val errorReader = readErrorOutput(process, timeout, logger)
      try {
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
        errorReader.join()
      }
    }
}

private fun readOutputAndBlock(process: Process, timeout: Timeout, logger: Logger) {
  // join on CompletableFuture will help to process other tasks in FJP_
  runAsync {
    consume(process.inputStream, process, timeout, logger::info)
  }.join()
}

private fun readErrorOutput(process: Process, timeout: Timeout, logger: Logger): CompletableFuture<Void> {
  return runAsync {
    consume(process.errorStream, process, timeout, logger::warn)
  }
}

private fun consume(inputStream: InputStream, process: Process, timeout: Timeout, consume: (String) -> Unit) {
  inputStream.bufferedReader().use { reader ->
    var linesCount = 0
    var linesBuffer = StringBuilder()
    val flushTimeoutMs = 5000L
    var lastCharReceived = System.currentTimeMillis()
    while (!timeout.isElapsed && (process.isAlive || reader.ready())) {
      if (reader.ready()) {
        val char = reader.read().takeIf { it != -1 }?.toChar()
        if (char == '\n' || char == '\r') linesCount++
        if (char != null) {
          linesBuffer.append(char)
          lastCharReceived = System.currentTimeMillis()
        }
        if (char == null || !reader.ready() || linesCount > 100 || (System.currentTimeMillis() - lastCharReceived) > flushTimeoutMs) {
          if (linesBuffer.isNotEmpty()) {
            consume(linesBuffer.toString())
            linesBuffer = StringBuilder()
            linesCount = 0
          }
        }
      }
      else {
        Thread.sleep(100L)
      }
    }

    if (linesBuffer.isNotBlank()) {
      consume(linesBuffer.toString())
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
    get() = start.plus(millis).minus(System.currentTimeMillis()).takeIf { it > 0 } ?: 0
  val isElapsed: Boolean
    get() = remainingTime == 0L

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