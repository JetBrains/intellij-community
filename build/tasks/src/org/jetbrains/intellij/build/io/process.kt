// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.io

import com.fasterxml.jackson.jr.ob.JSON
import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.intellij.build.telemetry.useWithScope
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.toList
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import java.io.File
import java.io.InputStream
import java.nio.charset.MalformedInputException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

val DEFAULT_TIMEOUT: Duration = 10.minutes

/**
 * Executes a Java class in a forked JVM.
 */
suspend fun runJava(mainClass: String,
                    args: List<String>,
                    jvmArgs: List<String> = emptyList(),
                    classPath: Collection<String>,
                    javaExe: Path,
                    timeout: Duration = DEFAULT_TIMEOUT,
                    workingDir: Path? = null,
                    customOutputFile: Path? = null,
                    onError: (() -> Unit)? = null) {
  val workingDir = workingDir ?: Path.of(System.getProperty("user.dir"))
  val useJsonOutput = jvmArgs.any { it == "-Dintellij.log.to.json.stdout=true" }
  val commandLine = buildString {
    append(mainClass)
    if (args.any()) {
      append(args.joinToString(prefix = " ", separator = " "))
    }
  }
  spanBuilder(commandLine)
    .setAttribute(AttributeKey.stringArrayKey("jvmArgs"), jvmArgs)
    .setAttribute("workingDir", "$workingDir")
    .setAttribute("timeoutMillis", "$timeout")
    .useWithScope(Dispatchers.IO) { span ->
      val toDelete = ArrayList<Path>(3)
      var process: Process? = null
      try {
        val classpathFile = Files.createTempFile("classpath-", ".txt").also(toDelete::add)
        val classPathStringBuilder = createClassPathFile(classPath, classpathFile)
        val processArgs = createProcessArgs(javaExe = javaExe,
                                            jvmArgs = jvmArgs,
                                            classpathFile = classpathFile,
                                            mainClass = mainClass,
                                            args = args)
        span.setAttribute(AttributeKey.stringArrayKey("processArgs"), processArgs)
        val errorOutputFile = Files.createTempFile("error-out-", ".txt").also(toDelete::add)
        val outputFile = customOutputFile?.also { customOutputFile.parent?.let { Files.createDirectories(it) } }
                         ?: Files.createTempFile("out-", ".txt").also(toDelete::add)
        logFreeDiskSpace(workingDir, "before $commandLine")
        process = ProcessBuilder(processArgs)
          .directory(workingDir.toFile())
          .redirectError(errorOutputFile.toFile())
          .redirectOutput(outputFile.toFile())
          .start()

        span.setAttribute("pid", process.pid())

        fun javaRunFailed(reason: String) {
          span.setAttribute("classPath", classPathStringBuilder.substring("-classpath".length))
          span.setAttribute("processArgs", processArgs.joinToString(separator = " "))
          span.setAttribute("output", runCatching { Files.readString(outputFile) }.getOrNull() ?: "output file doesn't exist")
          val errorOutput = runCatching { Files.readString(errorOutputFile) }.getOrNull()
          val output = runCatching { Files.readString(outputFile) }.getOrNull()
          val errorMessage = StringBuilder("Cannot execute $mainClass: $reason\n${processArgs.joinToString(separator = " ")}" +
                                           "\n--- error output ---\n" +
                                           "$errorOutput")
          if (!useJsonOutput) {
            errorMessage.append("\n--- output ---\n$output\n")
          }
          errorMessage.append("\n--- ---")
          span.setAttribute("errorOutput", errorOutput ?: "error output file doesn't exist")
          onError?.invoke()
          throw RuntimeException(errorMessage.toString())
        }

        try {
          withTimeout(timeout) {
            while (process.isAlive) {
              delay(5.milliseconds)
            }
          }
        }
        catch (e: TimeoutCancellationException) {
          try {
            dumpThreads(process.pid())
          }
          catch (e: Exception) {
            span.addEvent("cannot dump threads: ${e.message}")
          }

          process.destroyForcibly().waitFor()
          javaRunFailed(e.message!!)
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
          javaRunFailed("exitCode=$exitCode")
        }

        if (useJsonOutput) {
          checkOutput(outputFile = outputFile, span = span, errorConsumer = ::javaRunFailed)
        }
      }
      finally {
        process?.waitFor()
        toDelete.forEach(FileUtilRt::deleteRecursively)
        logFreeDiskSpace(workingDir, "after $commandLine")
      }
    }
}

private fun checkOutput(outputFile: Path, span: Span, errorConsumer: (String) -> Unit) {
  val out = try {
    try {
      Files.readString(outputFile)
    }
    catch (_: MalformedInputException) {
      Files.readString(outputFile, Charsets.ISO_8859_1)
    }
  }
  catch (_: NoSuchFieldException) {
    span.setAttribute("output", "output file doesn't exist")
    return
  }

  val messages = StringBuilder()
  out.lineSequence()
    .filter { it.isNotBlank() }
    .forEach { line ->
      if (line.startsWith('{')) {
        val item = JSON.std.mapFrom(line)
        val message = (item.get("message") as? String) ?: error("Missing field: 'message' in $line")
        val level = (item.get("level") as? String) ?: error("Missing field: 'level' in $line")
        messages.append(message).append('\n')
        if (level == "SEVERE") {
          errorConsumer("Error reported from child process logger: $message")
        }
      }
      else {
        messages.append(line).append('\n')
      }
    }
  span.setAttribute("output", messages.toString())
}

private fun createProcessArgs(javaExe: Path,
                              jvmArgs: List<String>,
                              classpathFile: Path?,
                              mainClass: String,
                              args: List<String>): MutableList<String> {
  val processArgs = mutableListOf<String>()
  processArgs.add(javaExe.toString())
  processArgs.add("-Djava.awt.headless=true")
  processArgs.add("-Dapple.awt.UIElement=true")
  processArgs.addAll(jvmArgs)
  processArgs.add("@$classpathFile")
  processArgs.add(mainClass)
  processArgs.addAll(args)
  return processArgs
}

private fun createClassPathFile(classPath: Collection<String>, classpathFile: Path): StringBuilder {
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
@Obsolete
fun runProcessBlocking(args: List<String>, workingDir: Path? = null, timeoutMillis: Long = DEFAULT_TIMEOUT.inWholeMilliseconds) {
  runBlocking {
    runProcess(args = args,
               workingDir = workingDir,
               timeout = timeoutMillis.milliseconds,
               additionalEnvVariables = emptyMap(),
               inheritOut = false)
  }
}

suspend fun runProcess(vararg args: String,
                       workingDir: Path? = null,
                       timeout: Duration = DEFAULT_TIMEOUT,
                       additionalEnvVariables: Map<String, String> = emptyMap(),
                       inheritOut: Boolean = false) {
  runProcess(args.toList(), workingDir, timeout, additionalEnvVariables, inheritOut)
}

suspend fun runProcess(
  vararg args: String,
  workingDir: Path? = null,
  timeout: Duration = DEFAULT_TIMEOUT,
  additionalEnvVariables: Map<String, String> = emptyMap(),
  stdOutConsumer: (line: String) -> Unit,
  stdErrConsumer: (line: String) -> Unit,
) {
  runProcess(
    args.toList(), workingDir, timeout, additionalEnvVariables, inheritOut = false,
    stdOutConsumer = stdOutConsumer,
    stdErrConsumer = stdErrConsumer,
  )
}

suspend fun runProcess(
  args: List<String>,
  workingDir: Path? = null,
  timeout: Duration = DEFAULT_TIMEOUT,
  additionalEnvVariables: Map<String, String> = emptyMap(),
  inheritOut: Boolean = false,
  inheritErrToOut: Boolean = false,
  attachStdOutToException: Boolean = false,
  stdOutConsumer: (line: String) -> Unit = {},
  stdErrConsumer: (line: String) -> Unit = {},
) {
  val workingDir = workingDir ?: Path.of(System.getProperty("user.dir"))
  val commandLine = args.joinToString(separator = " ")
  spanBuilder(commandLine)
    .setAttribute("workingDir", "$workingDir")
    .setAttribute("timeoutMillis", "$timeout")
    .useWithScope(Dispatchers.IO) { span ->
        var process: Process? = null
        val phase = args.joinToString(separator = " ")
        try {
          logFreeDiskSpace(workingDir, "before $phase")
          process = ProcessBuilder(args)
            .directory(workingDir.toFile())
            .also { builder ->
              if (additionalEnvVariables.isNotEmpty()) {
                builder.environment().putAll(additionalEnvVariables)
              }
              if (inheritOut) {
                builder.inheritIO()
                builder.redirectErrorStream(inheritErrToOut)
              }
            }.start()
          val outputChannel = Channel<String>(capacity = Channel.UNLIMITED)
          if (!inheritOut) {
            launch(Dispatchers.Default) {
              withTimeout(timeout) {
                process.inputStream.consume(process) {
                  span.addEvent(it)
                  stdOutConsumer(it)
                  if (attachStdOutToException) {
                    try {
                      outputChannel.send(it)
                    }
                    catch (_: ClosedSendChannelException) {
                    }
                  }
                }
              }
            }
            launch(Dispatchers.Default) {
              withTimeout(timeout) {
                process.errorStream.consume(process) {
                  span.addEvent(it)
                  stdErrConsumer(it)
                  try {
                    outputChannel.send(it)
                  }
                  catch (_: ClosedSendChannelException) {
                  }
                }
              }
            }
          }

          val pid = process.pid()
          span.setAttribute("pid", pid)

          try {
            withTimeout(timeout) {
              while (process.isAlive) {
                delay(5.milliseconds)
              }
            }
          }
          catch (e: TimeoutCancellationException) {
            throw e.apply {
              addSuppressed(RuntimeException("Process '$commandLine' (pid=$pid) failed to complete in $timeout" + toLines(outputChannel)))
            }
          }
          finally {
            outputChannel.close()
          }

          val exitCode = process.exitValue()
          if (exitCode != 0) {
            throw RuntimeException("Process '$commandLine' (pid=$pid) finished with exitCode $exitCode" + toLines(outputChannel))
          }
        }
        finally {
          process?.waitFor()
          logFreeDiskSpace(workingDir, "after $phase")
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

internal suspend fun dumpThreads(pid: Long) {
  val jstack = System.getenv("JAVA_HOME")
                 ?.removeSuffix("/")
                 ?.removeSuffix("\\")
                 ?.let { "$it/bin/jstack" }
               ?: "jstack"
  runProcess(args = listOf(jstack, pid.toString()), inheritOut = true)
}

private suspend fun InputStream.consume(process: Process, consume: suspend (line: String) -> Unit) {
  bufferedReader().use { reader ->
    var isLine = false
    var linesBuffer = StringBuilder()
    while (process.isAlive || reader.ready()) {
      if (reader.ready()) {
        val char = reader.read().takeIf { it != -1 }?.toChar()
        when {
          char == '\n' || char == '\r' -> isLine = true
          char != null -> linesBuffer.append(char)
        }
        if (char == null || !reader.ready() || isLine) {
          consume(linesBuffer.toString())
          linesBuffer = StringBuilder()
          isLine = false
        }
      }
      else {
        delay(5.milliseconds)
      }
    }
  }
}

private suspend fun toLines(channel: Channel<String>): String {
  channel.close()
  val lines = channel.toList()
  return if (lines.any()) {
    lines.joinToString(prefix = ":\n", separator = "\n")
  }
  else {
    ""
  }
}