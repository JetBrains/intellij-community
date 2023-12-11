// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.io

import com.fasterxml.jackson.jr.ob.JSON
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import java.io.File
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
                    classPath: List<String>,
                    javaExe: Path,
                    timeout: Duration = DEFAULT_TIMEOUT,
                    workingDir: Path? = null,
                    customOutputFile: Path? = null,
                    onError: (() -> Unit)? = null) {
  val jvmArgsWithJson = jvmArgs + "-Dintellij.log.to.json.stdout=true"
  @Suppress("NAME_SHADOWING")
  val workingDir = workingDir ?: Path.of(System.getProperty("user.dir"))
  spanBuilder("runJava")
    .setAttribute("mainClass", mainClass)
    .setAttribute(AttributeKey.stringArrayKey("args"), args)
    .setAttribute(AttributeKey.stringArrayKey("jvmArgs"), jvmArgsWithJson)
    .setAttribute("workingDir", "$workingDir")
    .setAttribute("timeoutMillis", timeout.toString())
    .useWithScope(Dispatchers.IO) { span ->
      val toDelete = ArrayList<Path>(3)
      var process: Process? = null
      val phase = args.joinToString(prefix = "$mainClass ", separator = " ")
      try {
        val classpathFile = Files.createTempFile("classpath-", ".txt").also(toDelete::add)
        val classPathStringBuilder = createClassPathFile(classPath, classpathFile)
        val processArgs = createProcessArgs(javaExe = javaExe,
                                            jvmArgs = jvmArgsWithJson,
                                            classpathFile = classpathFile,
                                            mainClass = mainClass,
                                            args = args)
        span.setAttribute(AttributeKey.stringArrayKey("processArgs"), processArgs)
        val errorOutputFile = Files.createTempFile("error-out-", ".txt").also(toDelete::add)
        val outputFile = customOutputFile?.also { customOutputFile.parent?.let { Files.createDirectories(it) } }
                         ?: Files.createTempFile("out-", ".txt").also(toDelete::add)
        logFreeDiskSpace(workingDir, "before $phase")
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
          span.setAttribute("errorOutput", errorOutput ?: "error output file doesn't exist")
          onError?.invoke()
          throw RuntimeException("Cannot execute $mainClass: $reason\n${processArgs.joinToString(separator = " ")}" +
                                 "\n--- error output ---\n" +
                                 "$errorOutput" +
                                 "\n--- output ---" +
                                 "$output\n" +
                                 "\n--- ---")
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

        if (customOutputFile == null) {
          checkOutput(outputFile = outputFile, span = span, errorConsumer = ::javaRunFailed)
        }
      }
      finally {
        process?.waitFor()
        toDelete.forEach(FileUtilRt::deleteRecursively)
        logFreeDiskSpace(workingDir, "after $phase")
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
  catch (e: NoSuchFieldException) {
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

suspend fun runProcess(args: List<String>,
                       workingDir: Path? = null,
                       timeout: Duration = DEFAULT_TIMEOUT,
                       additionalEnvVariables: Map<String, String> = emptyMap(),
                       inheritOut: Boolean = false,
                       inheritErrToOut: Boolean = false) {
  @Suppress("NAME_SHADOWING")
  val workingDir = workingDir ?: Path.of(System.getProperty("user.dir"))
  spanBuilder("runProcess")
    .setAttribute(AttributeKey.stringArrayKey("args"), args)
    .setAttribute("workingDir", "$workingDir")
    .setAttribute("timeoutMillis", timeout.toString())
    .useWithScope { span ->
      withContext(Dispatchers.IO) {
        val toDelete = ArrayList<Path>(3)
        var process: Process? = null
        val phase = args.joinToString(separator = " ")
        try {
          val errorOutputFile = if (inheritOut) null else Files.createTempFile("error-out-", ".txt").also(toDelete::add)
          val outputFile = if (inheritOut) null else Files.createTempFile("out-", ".txt").also(toDelete::add)
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
              else {
                builder.redirectOutput(outputFile!!.toFile())
                builder.redirectError(errorOutputFile!!.toFile())
              }
            }
            .start()
          val pid = process.pid()
          span.setAttribute("pid", pid)

          fun errorOccurred() {
            if (inheritOut) {
              return
            }

            span.setAttribute("output", runCatching { Files.readString(outputFile) }.getOrNull() ?: "output file doesn't exist")
            span.setAttribute("errorOutput",
                              runCatching { Files.readString(errorOutputFile) }.getOrNull() ?: "error output file doesn't exist")
          }

          try {
            withTimeout(timeout) {
              while (process.isAlive) {
                delay(5.milliseconds)
              }
            }
          }
          catch (e: TimeoutCancellationException) {
            process.destroyForcibly().waitFor()
            errorOccurred()
            throw e
          }

          val exitCode = process.exitValue()
          if (exitCode != 0) {
            errorOccurred()
            throw RuntimeException("Process $pid finished with exitCode $exitCode)")
          }

          if (!inheritOut) {
            checkOutput(outputFile!!, span) {
              errorOccurred()
              throw RuntimeException(it)
            }
          }
        }
        finally {
          process?.waitFor()
          toDelete.forEach(FileUtilRt::deleteRecursively)
          logFreeDiskSpace(workingDir, "after $phase")
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

internal suspend fun dumpThreads(pid: Long) {
  val jstack = System.getenv("JAVA_HOME")
                 ?.removeSuffix("/")
                 ?.removeSuffix("\\")
                 ?.let { "$it/bin/jstack" }
               ?: "jstack"
  runProcess(args = listOf(jstack, pid.toString()), inheritOut = true)
}
