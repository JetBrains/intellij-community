// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.fasterxml.jackson.jr.ob.JSON
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.buildScripts.concurrency.TaskContext
import com.intellij.platform.buildScripts.concurrency.TaskFailedException
import com.intellij.platform.buildScripts.concurrency.taskScope
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.ProcessBuilder.Redirect
import java.nio.charset.MalformedInputException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

val DEFAULT_TIMEOUT: Duration = 10.minutes

/**
 * Executes a Java class in a forked JVM, and blocks the calling thread until the process ends.
 *
 * A process that runs longer than [timeout] gets a thread dump and is killed. An interrupt of the calling thread kills
 * the process and is rethrown.
 */
fun runJava(
  mainClass: String,
  args: List<String>,
  jvmArgs: List<String> = emptyList(),
  classPath: Collection<String>,
  javaExe: Path,
  timeout: Duration = DEFAULT_TIMEOUT,
  workingDir: Path? = null,
  customOutput: Redirect? = null,
  customError: Redirect? = null,
  onError: (() -> Unit)? = null,
) {
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
    .use { span ->
      val toDelete = ArrayList<Path>(3)
      var process: Process? = null
      var failure: Throwable? = null
      fun processRedirect(customRedirect: Redirect?, prefix: String): Pair<Path?, Redirect?> {
        var outputFile: Path? = null
        val outputRedirect = if (customRedirect != null) {
          if (customRedirect != Redirect.DISCARD && (customRedirect.type() == Redirect.Type.WRITE || customRedirect.type() == Redirect.Type.APPEND)) {
            outputFile = customRedirect.file()?.toPath()
            outputFile?.parent?.let { Files.createDirectories(it) }
          }
          customRedirect
        }
        else {
          val file = Files.createTempFile(prefix, ".txt").also(toDelete::add)
          outputFile = file
          @Suppress("IO_FILE_USAGE")
          Redirect.to(file.toFile())
        }
        return Pair(outputFile, outputRedirect)
      }

      try {
        val classpathFile = Files.createTempFile("classpath-", ".txt").also(toDelete::add)
        val classPathStringBuilder = createClassPathFile(classPath, classpathFile)
        val processArgs = createProcessArgs(javaExe, jvmArgs, classpathFile, mainClass, args)
        span.setAttribute(AttributeKey.stringArrayKey("processArgs"), processArgs)
        val (outputFile, outputRedirect) = processRedirect(customOutput, "out-")
        val (errorOutputFile, errorRedirect) = processRedirect(customError, "error-out-")
        logFreeDiskSpace(workingDir, "before $commandLine")
        @Suppress("IO_FILE_USAGE")
        process = ProcessBuilder(processArgs)
          .directory(workingDir.toFile())
          .redirectError(errorRedirect)
          .redirectOutput(outputRedirect)
          .start()

        span.setAttribute("pid", process.pid())

        fun javaRunFailed(reason: String) {
          span.setAttribute("classPath", classPathStringBuilder.substring("-classpath".length))
          span.setAttribute("processArgs", processArgs.joinToString(separator = " "))
          val output = runCatching { outputFile?.let(Files::readString) }.getOrNull()
          span.setAttribute("output", output ?: "output file doesn't exist")
          val errorOutput = runCatching { errorOutputFile?.let(Files::readString) }.getOrNull()
          val errorMessage = StringBuilder(
            "Cannot execute $mainClass: $reason\n${processArgs.joinToString(separator = " ")}" +
            "\n--- error output ---\n" +
            "$errorOutput"
          )
          if (!useJsonOutput) {
            errorMessage.append("\n--- output ---\n$output\n")
          }
          errorMessage.append("\n--- ---")
          span.setAttribute("errorOutput", errorOutput ?: "error output file doesn't exist")
          onError?.invoke()
          throw RuntimeException(errorMessage.toString())
        }

        if (!process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
          try {
            dumpThreads(pid = process.pid(), javaExe = javaExe)
          }
          catch (error: InterruptedException) {
            throw error
          }
          catch (e: Exception) {
            span.addEvent("cannot dump threads: ${e.message}")
          }

          process.destroyForcibly().waitFor()
          javaRunFailed("Timed out waiting for $timeout")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
          javaRunFailed("exitCode=$exitCode")
        }

        if (useJsonOutput) {
          checkOutput(outputFile, span, errorConsumer = ::javaRunFailed)
        }
      }
      catch (error: Throwable) {
        failure = error
        throw error
      }
      finally {
        try {
          closeProcess(process, emptyList(), failure)
        }
        finally {
          toDelete.forEach(FileUtilRt::deleteRecursively)
          logFreeDiskSpace(workingDir, "after $commandLine")
        }
      }
    }
}

private fun checkOutput(outputFile: Path?, span: Span, errorConsumer: (String) -> Unit) {
  if (outputFile == null) {
    span.setAttribute("output", "output file is null")
    return
  }
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
        val message = (item["message"] as? String) ?: error("Missing field: 'message' in $line")
        val level = (item["level"] as? String) ?: error("Missing field: 'level' in $line")
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

private fun createProcessArgs(
  javaExe: Path,
  jvmArgs: List<String>,
  classpathFile: Path?,
  mainClass: String,
  args: List<String>,
): MutableList<String> {
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
    @Suppress("IO_FILE_USAGE")
    classPathStringBuilder.append(java.io.File.pathSeparator)
  }
  classPathStringBuilder.setLength(classPathStringBuilder.length - 1)
  Files.writeString(classpathFile, classPathStringBuilder)
  return classPathStringBuilder
}

@JvmOverloads
@ApiStatus.Obsolete
@Deprecated("Use runProcess, which blocks too", ReplaceWith("runProcess(args, workingDir, timeoutMillis.milliseconds)"))
fun runProcessBlocking(args: List<String>, workingDir: Path? = null, timeoutMillis: Long = DEFAULT_TIMEOUT.inWholeMilliseconds) {
  runProcess(args, workingDir, timeoutMillis.milliseconds)
}

/**
 * Runs a process and blocks the calling thread until it ends.
 *
 * Two virtual threads read the output unless [inheritOut] is set. The timeout includes execution and output consumption.
 * A timeout, an interrupt, or a consumer failure kills the process and stops both readers before this function throws.
 * Consumers must respond to interruption. Output available when the direct process exits is drained without waiting for its descendants.
 */
fun runProcess(
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
    .use { span ->
      var process: Process? = null
      val pumps = ArrayList<ProcessOutputPump>(2)
      var failure: Throwable? = null
      try {
        taskScope(name = commandLine, timeout = timeout.takeIf { it.isFinite() }) {
          try {
            checkCancelled()
            logFreeDiskSpace(workingDir, "before $commandLine")
            @Suppress("IO_FILE_USAGE")
            val running = ProcessBuilder(args)
              .directory(workingDir.toFile())
              .also { builder ->
                builder.environment().putAll(additionalEnvVariables)
                if (inheritOut) {
                  if (inheritErrToOut) {
                    builder.inheritIO()
                    builder.redirectErrorStream(true)
                  }
                  else {
                    // stderr stays piped, so a failure message can carry its tail
                    builder.redirectInput(Redirect.INHERIT)
                    builder.redirectOutput(Redirect.INHERIT)
                  }
                }
              }.start()
            process = running
            val outputTail = OutputTail()
            if (!inheritOut) {
              pumps.add(ProcessOutputPump("stdout of $commandLine", running, running.inputStream) {
                span.addEvent(it)
                stdOutConsumer(it)
                if (attachStdOutToException) outputTail.add(it)
              })
              pumps.add(ProcessOutputPump("stderr of $commandLine", running, running.errorStream) {
                span.addEvent(it)
                stdErrConsumer(it)
                outputTail.add(it)
              })
            }
            else if (!inheritErrToOut) {
              pumps.add(ProcessOutputPump("stderr of $commandLine", running, running.errorStream) {
                System.err.println(it)
                stdErrConsumer(it)
                outputTail.add(it)
              })
            }
            val pid = running.pid()
            span.setAttribute("pid", pid)
            for (pump in pumps) fork(pump.name) { pump.run(this) }
            val exit = fork("wait for $commandLine") { running.waitFor() }
            try {
              join()
            }
            catch (error: TaskFailedException) {
              throw error.cause ?: error
            }
            catch (_: TimeoutException) {
              throw TimeoutException("Process '$commandLine' (pid=$pid) failed to complete in $timeout" + outputTail.format())
            }
            val exitCode = exit.get()
            if (exitCode != 0) {
              throw RuntimeException("Process '$commandLine' (pid=$pid) finished with exitCode $exitCode" + outputTail.format())
            }
          }
          catch (error: Throwable) {
            failure = error
            throw error
          }
          finally {
            closeProcess(process, pumps, failure)
          }
        }
      }
      catch (error: Throwable) {
        failure = error
        throw error
      }
      finally {
        for (pump in pumps) {
          val cleanupFailure = pump.failure
          val primary = failure
          if (primary != null && cleanupFailure != null && primary !== cleanupFailure &&
              cleanupFailure !is InterruptedException && cleanupFailure !is java.util.concurrent.CancellationException) {
            primary.addSuppressed(cleanupFailure)
          }
          try {
            pump.stream.close()
          }
          catch (error: Throwable) {
            val primary = failure ?: throw error
            if (primary !== error) primary.addSuppressed(error)
          }
        }
        logFreeDiskSpace(workingDir, "after $commandLine")
      }
    }
}

/** Owns one output reader. Only stream errors caused by an explicit stop are ignored. */
private class ProcessOutputPump(
  val name: String,
  private val process: Process,
  val stream: InputStream,
  private val consume: (String) -> Unit,
) {
  @Volatile
  private var stopping = false

  @Volatile
  var failure: Throwable? = null
    private set

  fun run(context: TaskContext) {
    try {
      stream.use { readLines(context) }
    }
    catch (error: Throwable) {
      failure = error
      throw error
    }
  }

  fun stop() {
    stopping = true
  }

  private fun checkActive(context: TaskContext) {
    if (stopping || context.isCancelled || Thread.interrupted()) {
      throw InterruptedException()
    }
  }

  private fun readLines(context: TaskContext) {
    val buffer = ByteArray(8192)
    val line = ByteArrayOutputStream()
    var previousWasCarriageReturn = false
    while (true) {
      checkActive(context)
      var count = readAvailable(buffer)
      if (count == 0 && !process.isAlive) {
        count = readAvailable(buffer)
        if (count == 0) {
          break
        }
      }
      if (count < 0) {
        break
      }
      if (count == 0) {
        Thread.sleep(5)
        continue
      }
      for (index in 0 until count) {
        val value = buffer[index].toInt() and 0xff
        if (value == '\r'.code || value == '\n'.code) {
          if (value != '\n'.code || !previousWasCarriageReturn) {
            checkActive(context)
            consume(line.toString(Charsets.UTF_8))
            line.reset()
          }
          previousWasCarriageReturn = value == '\r'.code
        }
        else {
          line.write(value)
          previousWasCarriageReturn = false
        }
      }
    }
    if (line.size() > 0) {
      checkActive(context)
      consume(line.toString(Charsets.UTF_8))
    }
  }

  private fun readAvailable(buffer: ByteArray): Int {
    try {
      val available = stream.available()
      return if (available == 0) 0 else stream.read(buffer, 0, minOf(available, buffer.size))
    }
    catch (error: IOException) {
      if (stopping) {
        return -1
      }
      throw error
    }
  }
}

private fun closeProcess(process: Process?, pumps: List<ProcessOutputPump>, primaryFailure: Throwable?) {
  var interrupted = Thread.interrupted() || primaryFailure is InterruptedException
  var failure = primaryFailure
  fun recordFailure(error: Throwable) {
    val previous = failure
    if (previous == null) {
      failure = error
    }
    else {
      previous.addSuppressed(error)
    }
  }

  fun awaitTermination(action: () -> Unit) {
    while (true) {
      try {
        action()
        return
      }
      catch (_: InterruptedException) {
        interrupted = true
      }
    }
  }
  try {
    for (pump in pumps) {
      pump.stop()
    }
    try {
      if (process != null) {
        if (process.isAlive) {
          process.destroyForcibly()
        }
        awaitTermination { process.waitFor() }
        process.outputStream.close()
      }
    }
    catch (error: Throwable) {
      recordFailure(error)
    }

  }
  finally {
    if (interrupted) {
      Thread.currentThread().interrupt()
    }
  }
  if (primaryFailure == null) {
    failure?.let { throw it }
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

/**
 * Dumps the threads of the process [pid].
 *
 * Uses the `jstack` next to [javaExe], the JDK that runs the process, and falls back to `JAVA_HOME` and then to `PATH`.
 *
 * Use `jstack`, and not the Attach API. The IDE adds the coroutine dump, the progress indicator state, and the lock
 * state to the `jstack` output through the JBR hook. See `ApplicationLoader.enableJstack`. The Attach API gives the
 * same dump, but it needs `--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED`, which the build JVM does not have.
 */
internal fun dumpThreads(pid: Long, javaExe: Path) {
  val jstackName = if (javaExe.fileName.toString().endsWith(".exe")) "jstack.exe" else "jstack"
  val jstack = javaExe.resolveSibling(jstackName).takeIf { Files.isRegularFile(it) }?.toString()
               ?: System.getenv("JAVA_HOME")
                 ?.removeSuffix("/")
                 ?.removeSuffix("\\")
                 ?.let { "$it/bin/$jstackName" }
               ?: jstackName
  runProcess(args = listOf(jstack, pid.toString()), inheritOut = true)
}

private const val ATTACHED_OUTPUT_TAIL_LINES = 100

/** Keeps the last [ATTACHED_OUTPUT_TAIL_LINES] lines, so a verbose process does not grow the heap. */
private class OutputTail {
  private val lines = ArrayDeque<String>()
  private var omitted = 0

  @Synchronized
  fun add(line: String) {
    if (lines.size == ATTACHED_OUTPUT_TAIL_LINES) {
      lines.removeFirst()
      omitted++
    }
    lines.add(line)
  }

  @Synchronized
  fun format(): String {
    if (lines.isEmpty()) return ""
    val prefix = if (omitted > 0) ":\n[$omitted earlier output lines omitted]\n" else ":\n"
    return lines.joinToString(prefix = prefix, separator = "\n")
  }
}
