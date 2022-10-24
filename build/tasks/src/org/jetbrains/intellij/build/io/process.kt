// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("BlockingMethodInNonBlockingContext", "ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.io

import com.fasterxml.jackson.jr.ob.JSON
import com.intellij.diagnostic.telemetry.useWithScope2
import com.intellij.openapi.util.io.FileUtilRt
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import java.io.File
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
  spanBuilder("runJava")
    .setAttribute("mainClass", mainClass)
    .setAttribute(AttributeKey.stringArrayKey("args"), args)
    .setAttribute(AttributeKey.stringArrayKey("jvmArgs"), jvmArgsWithJson)
    .setAttribute("workingDir", workingDir?.toString() ?: "")
    .setAttribute("timeoutMillis", timeout.toString())
    .useWithScope2 { span ->
      withContext(Dispatchers.IO) {
        val toDelete = ArrayList<Path>(3)
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
          val process = ProcessBuilder(processArgs)
            .directory(workingDir?.toFile())
            .redirectError(errorOutputFile.toFile())
            .redirectOutput(outputFile.toFile())
            .start()

          span.setAttribute("pid", process.pid())

          fun javaRunFailed(reason: String) {
            span.setAttribute("classPath", classPathStringBuilder.substring("-classpath".length))
            span.setAttribute("output", runCatching { Files.readString(outputFile) }.getOrNull() ?: "output file doesn't exist")
            span.setAttribute("errorOutput",
                              runCatching { Files.readString(errorOutputFile) }.getOrNull() ?: "error output file doesn't exist")
            onError?.invoke()
            throw RuntimeException("Cannot execute $mainClass: $reason")
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
          toDelete.forEach(FileUtilRt::deleteRecursively)
        }
      }
    }
}

private fun checkOutput(outputFile: Path, span: Span, errorConsumer: (String) -> Unit) {
  val out = try {
    Files.readString(outputFile)
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

fun runProcessBlocking(args: List<String>, workingDir: Path? = null) {
  runBlocking {
    runProcess(args = args,
               workingDir = workingDir,
               timeout = DEFAULT_TIMEOUT,
               additionalEnvVariables = emptyMap(),
               inheritOut = false)
  }
}

suspend fun runProcess(args: List<String>,
                       workingDir: Path? = null,
                       timeout: Duration = DEFAULT_TIMEOUT,
                       additionalEnvVariables: Map<String, String> = emptyMap(),
                       inheritOut: Boolean = false) {
  spanBuilder("runProcess")
    .setAttribute(AttributeKey.stringArrayKey("args"), args)
    .setAttribute("timeoutMillis", timeout.toString())
    .useWithScope2 { span ->
      withContext(Dispatchers.IO) {
        val toDelete = ArrayList<Path>(3)
        try {
          val errorOutputFile = if (inheritOut) null else Files.createTempFile("error-out-", ".txt").also(toDelete::add)
          val outputFile = if (inheritOut) null else Files.createTempFile("out-", ".txt").also(toDelete::add)
          val process = ProcessBuilder(args)
            .directory(workingDir?.toFile())
            .also { builder ->
              if (additionalEnvVariables.isNotEmpty()) {
                builder.environment().putAll(additionalEnvVariables)
              }
              if (inheritOut) {
                builder.inheritIO()
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
          toDelete.forEach(FileUtilRt::deleteRecursively)
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

class ProcessRunTimedOut(message: String) : RuntimeException(message)

internal suspend fun dumpThreads(pid: Long) {
  val jstack = System.getenv("JAVA_HOME")
                 ?.removeSuffix("/")
                 ?.removeSuffix("\\")
                 ?.let { "$it/bin/jstack" }
               ?: "jstack"
  runProcess(args = listOf(jstack, pid.toString()), inheritOut = true)
}