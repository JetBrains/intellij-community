// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.diagnostic.telemetry.use
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import org.jetbrains.intellij.build.BuildScriptsLoggedError
import org.jetbrains.intellij.build.tracer
import java.io.File
import java.io.InputStream
import java.lang.System.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Executes a Java class in a forked JVM.
 */
fun runJava(mainClass: String,
            args: Iterable<String>,
            jvmArgs: List<String> = emptyList(),
            classPath: List<String>,
            javaExe: Path,
            logger: Logger = System.getLogger(mainClass),
            timeoutMillis: Long = Timeout.DEFAULT,
            workingDir: Path? = null,
            onError: (() -> Unit)? = null) {
  val timeout = Timeout(timeoutMillis)
  var errorReader: CompletableFuture<Void>? = null
  val classpathFile = Files.createTempFile("classpath-", ".txt")
  val jvmArgsWithJson = jvmArgs + "-Dintellij.log.to.json.stdout=true"
  tracer.spanBuilder("runJava")
    .setAttribute("mainClass", mainClass)
    .setAttribute(AttributeKey.stringArrayKey("args"), args.toList())
    .setAttribute(AttributeKey.stringArrayKey("jvmArgs"), jvmArgsWithJson.toList())
    .setAttribute("workingDir", workingDir?.toString() ?: "")
    .setAttribute("timeoutMillis", timeoutMillis)
    .use { span ->
      try {
        val classPathStringBuilder = createClassPathFile(classPath, classpathFile)
        val processArgs = createProcessArgs(javaExe = javaExe, jvmArgs = jvmArgsWithJson, classpathFile = classpathFile, mainClass = mainClass, args = args)
        span.setAttribute(AttributeKey.stringArrayKey("processArgs"), processArgs)
        val process = ProcessBuilder(processArgs).directory(workingDir?.toFile()).start()

        val firstError = AtomicReference<String>()
        errorReader = readErrorOutput(process, timeout, logger)
        readOutputAndBlock(process, timeout, logger, firstError)

        fun javaRunFailed(reason: String) {
          Span.current().setAttribute("classPath", classPathStringBuilder.substring("-classpath".length))
          val message = "$reason\nCannot execute $mainClass (pid=${process.pid()} args=$args, vmOptions=$jvmArgsWithJson)"
          span.setStatus(StatusCode.ERROR, message)
          onError?.invoke()
          throw RuntimeException(message)
        }

        val errorMessage = firstError.get()
        if (errorMessage != null) {
          javaRunFailed("Error reported from child process logger: $errorMessage")
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

fun runJavaWithOutputToFile(mainClass: String,
                            args: List<String>,
                            jvmArgs: List<String>,
                            classPath: List<String>,
                            javaExe: Path,
                            timeoutMillis: Long = Timeout.DEFAULT,
                            outputFile: Path,
                            workingDir: Path? = null) {
  Files.createDirectories(outputFile.parent)

  val timeout = Timeout(timeoutMillis)
  val classpathFile = Files.createTempFile("classpath-", ".txt")
  tracer.spanBuilder("runJava")
    .setAttribute("mainClass", mainClass)
    .setAttribute(AttributeKey.stringArrayKey("args"), args)
    .setAttribute(AttributeKey.stringArrayKey("jvmArgs"), jvmArgs)
    .setAttribute("outputFile", outputFile.toString())
    .setAttribute("workingDir", workingDir?.toString() ?: "")
    .setAttribute("timeoutMillis", timeoutMillis)
    .use { span ->
      try {
        createClassPathFile(classPath, classpathFile)
        val processArgs = createProcessArgs(javaExe = javaExe, jvmArgs = jvmArgs, classpathFile = classpathFile, mainClass = mainClass,
                                            args = args)
        span.setAttribute(AttributeKey.stringArrayKey("processArgs"), processArgs)
        val process = ProcessBuilder(processArgs)
          .directory(workingDir?.toFile())
          .redirectErrorStream(true)
          .redirectOutput(outputFile.toFile())
          .start()

        fun javaRunFailed(exception: (String) -> Exception = ::RuntimeException) {
          val message = "Cannot execute $mainClass, see details in ${outputFile.fileName} (published to TeamCity build artifacts), exitCode=${process.exitValue()}, pid=${process.pid()}, args=$args, vmOptions=$jvmArgs"
          span.setStatus(StatusCode.ERROR, message)
          if (Files.exists(outputFile)) {
            span.setAttribute("processOutput", Files.readString(outputFile))
          }
          throw exception(message)
        }

        if (!process.waitFor(timeout.remainingTime, TimeUnit.MILLISECONDS)) {
          process.destroyForcibly().waitFor()
          javaRunFailed { message ->
            ProcessRunTimedOut("$message: $timeout timeout")
          }
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
          javaRunFailed()
        }
      }
      finally {
        Files.deleteIfExists(classpathFile)
      }
    }
}

private fun createProcessArgs(javaExe: Path,
                              jvmArgs: Iterable<String>,
                              classpathFile: Path?,
                              mainClass: String,
                              args: Iterable<String>): MutableList<String> {
  val processArgs = mutableListOf<String>()
  // FIXME: enforce JBR
  processArgs.add(javaExe.toString())
  processArgs.add("-Djava.awt.headless=true")
  processArgs.add("-Dapple.awt.UIElement=true")
  processArgs.addAll(jvmArgs)
  processArgs.add("@$classpathFile")
  processArgs.add(mainClass)
  processArgs.addAll(args)
  return processArgs
}

private fun createClassPathFile(classPath: List<String>, classpathFile: Path): StringBuilder {
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
               logger: Logger? = null,
               timeoutMillis: Long = Timeout.DEFAULT) {
  runProcess(args.toList(), workingDir, logger, timeoutMillis)
}

@JvmOverloads
fun runProcess(args: List<String>,
               workingDir: Path? = null,
               logger: Logger? = null,
               timeoutMillis: Long = Timeout.DEFAULT,
               additionalEnvVariables: Map<String, String> = emptyMap()) {
  tracer.spanBuilder("runProcess")
    .setAttribute(AttributeKey.stringArrayKey("args"), args)
    .setAttribute("timeoutMillis", timeoutMillis)
    .use {
      val timeout = Timeout(timeoutMillis)
      val process = ProcessBuilder(args)
        .directory(workingDir?.toFile())
        .also { builder -> additionalEnvVariables.entries.forEach { (k, v) -> builder.environment()[k] = v } }
        .let { if (logger == null) it.inheritIO() else it }
        .start()
      val pid = process.pid()
      val errorReader = logger?.let { readErrorOutput(process, timeout, it) }
      try {
        if (logger != null) {
          readOutputAndBlock(process, timeout, logger)
        }

        if (!process.waitFor(timeout.remainingTime, TimeUnit.MILLISECONDS)) {
          process.destroyForcibly().waitFor()
          throw ProcessRunTimedOut("Cannot execute [$pid] $args: $timeout timeout")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
          throw RuntimeException("Cannot execute [$pid] $args (exitCode=$exitCode)")
        }
      }
      finally {
        errorReader?.join()
      }
    }
}

private fun readOutputAndBlock(process: Process,
                               timeout: Timeout,
                               logger: Logger,
                               firstError: AtomicReference<String>? = null) {
  val mapper = ObjectMapper()
  // join on CompletableFuture will help to process other tasks in FJP
  runAsync {
    consume(process.inputStream, process, timeout) {
      if (it.startsWith("{")) {
        try {
          val jObject = mapper.readTree(it)
          val message = jObject.get("message")?.asText() ?: error("Missing field: 'message'")
          when (val level = jObject.get("level")?.asText() ?: error("Missing field: 'level'")) {
            "SEVERE" -> {
              firstError?.compareAndSet(null, message)
              try {
                logger.error(message)
              } catch (_: BuildScriptsLoggedError) {
                // skip exception thrown by logger.error
                // we want to continue consuming stream
              }
            }
            "WARNING" -> {
              logger.warn(message)
            }
            "INFO" -> {
              logger.info(message)
            }
            "CONFIG" -> {
              logger.debug(message)
            }
            "FINE" -> {
              logger.debug(message)
            }
            "FINEST" -> {
              logger.debug(message)
            }
            "FINER" -> {
              logger.debug(message)
            }
            else -> {
              error("Unable parse log level: $level")
            }
          }
        } catch (e: Throwable)  {
          try {
            val message = "Unable to parse line: ${it}, error: ${e.message}\n${e.stackTraceToString()}"
            firstError?.compareAndSet(null, message)
            logger.error(message)
          }
          catch (_: BuildScriptsLoggedError) {
            // skip exception thrown by logger.error
            // we want to continue consuming stream
          }
        }
      } else {
        logger.info("[${process.pid()}] $it")
      }
    }
  }.join()
}

private fun readErrorOutput(process: Process, timeout: Timeout, logger: Logger): CompletableFuture<Void> {
  return runAsync {
    consume(process.errorStream, process, timeout)  { logger.warn("[${process.pid()}] $it") }
  }
}

internal fun consume(inputStream: InputStream, process: Process, timeout: Timeout, consume: (String) -> Unit) {
  inputStream.bufferedReader().use { reader ->
    val lines = mutableListOf<String>()
    val lineBuffer = StringBuilder()
    val flushTimeoutMs = 5000L
    var lastCharReceived = System.nanoTime() * 1_000_000
    while (!timeout.isElapsed && (process.isAlive || reader.ready())) {
      if (reader.ready()) {
        val char = reader.read().takeIf { it != -1 }?.toChar()
        if (char != null) {
          if (char == '\n' || char == '\r') {
            if (lineBuffer.isNotEmpty()) {
              lines.add(lineBuffer.toString())
              lineBuffer.clear()
            }
          } else {
            lineBuffer.append(char)
          }
          lastCharReceived = System.nanoTime() * 1_000_000
        }
        if (char == null || !reader.ready() || lines.size > 100 || (System.nanoTime() * 1_000_000 - lastCharReceived) > flushTimeoutMs) {
          for (line in lines) {
            consume(line)
          }
          lines.clear()
        }
      }
      else {
        Thread.sleep(100L)
      }
    }

    if (lineBuffer.isNotBlank()) {
      consume(lineBuffer.toString())
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

class ProcessRunTimedOut(message: String) : RuntimeException(message)

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