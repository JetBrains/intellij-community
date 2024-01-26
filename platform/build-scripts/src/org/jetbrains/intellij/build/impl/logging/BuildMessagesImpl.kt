// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging

import com.intellij.platform.diagnostic.telemetry.helpers.use
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.TeamCityHelper.isUnderTeamCity
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Callable
import java.util.function.Consumer

class BuildMessagesImpl private constructor(private val logger: BuildMessageLogger,
                                            private val debugLogger: DebugLogger) : BuildMessages {
  companion object {
    fun create(): BuildMessagesImpl {
      val mainLoggerFactory = if (isUnderTeamCity) TeamCityBuildMessageLogger.FACTORY else ConsoleBuildMessageLogger.FACTORY
      val debugLogger = DebugLogger()
      return BuildMessagesImpl(logger = CompositeBuildMessageLogger(listOf(mainLoggerFactory(), debugLogger.createLogger())),
                               debugLogger = debugLogger)
    }
  }

  override fun getName() = ""

  override fun isLoggable(level: System.Logger.Level) = level.severity > System.Logger.Level.TRACE.severity

  override fun log(level: System.Logger.Level, bundle: ResourceBundle?, message: String, thrown: Throwable?) {
    when (level) {
      System.Logger.Level.INFO -> info(message)
      System.Logger.Level.ERROR -> {
        if (thrown == null) {
          error(message)
        }
        else {
          error(message, thrown)
        }
      }
      System.Logger.Level.WARNING -> warning(message)
      else -> debug(message)
    }
  }

  override fun log(level: System.Logger.Level, bundle: ResourceBundle?, format: String, vararg params: Any?) {
    log(level = level, message = format, bundle = bundle, thrown = null)
  }

  override fun info(message: String) {
    processMessage(LogMessage(LogMessage.Kind.INFO, message))
  }

  override fun warning(message: String) {
    processMessage(LogMessage(LogMessage.Kind.WARNING, message))
  }

  fun debug(message: String) {
    processMessage(LogMessage(LogMessage.Kind.DEBUG, message))
  }

  fun setDebugLogPath(path: Path) {
    debugLogger.setOutputFile(path)
  }

  override fun close() {
    debugLogger.close()
  }

  override val debugLogFile: Path?
    get() = debugLogger.getOutputFile()

  override fun error(message: String) {
    throw BuildScriptsLoggedError(message)
  }

  override fun error(message: String, cause: Throwable) {
    val writer = StringWriter()
    PrintWriter(writer).use(cause::printStackTrace)
    processMessage(LogMessage(kind = LogMessage.Kind.ERROR, text = """
       $message
       $writer
       """.trimIndent()))
    throw BuildScriptsLoggedError(message, cause)
  }

  override fun compilationErrors(compilerName: String, messages: List<String>) {
    processMessage(CompilationErrorsLogMessage(compilerName, messages))
  }

  override fun progress(message: String) {
    logger.processMessage(LogMessage(LogMessage.Kind.PROGRESS, message))
  }

  override fun buildStatus(message: String) {
    processMessage(LogMessage(LogMessage.Kind.BUILD_STATUS, message))
  }

  override fun changeBuildStatusToSuccess(message: String) {
    processMessage(LogMessage(LogMessage.Kind.BUILD_STATUS_CHANGED_TO_SUCCESSFUL, message))
  }

  override fun setParameter(parameterName: String, value: String) {
    processMessage(LogMessage(LogMessage.Kind.SET_PARAMETER, "$parameterName=$value"))
  }

  override fun block(blockName: String, task: Callable<Unit>) {
    runBlocking {
      TraceManager.exportPendingSpans()
    }

    try {
      processMessage(LogMessage(LogMessage.Kind.BLOCK_STARTED, blockName))
      spanBuilder(blockName.lowercase(Locale.getDefault())).use {
        try {
          task.call()
        }
        catch (e: Throwable) {
          // print all pending spans
          runBlocking {
            TraceManager.exportPendingSpans()
          }
          throw e
        }
      }
    }
    finally {
      runBlocking {
        TraceManager.exportPendingSpans()
      }
      processMessage(LogMessage(LogMessage.Kind.BLOCK_FINISHED, blockName))
    }
  }

  override fun artifactBuilt(relativeArtifactPath: String) {
    logger.processMessage(LogMessage(LogMessage.Kind.ARTIFACT_BUILT, relativeArtifactPath))
  }

  override fun reportStatisticValue(key: String, value: String) {
    processMessage(LogMessage(LogMessage.Kind.STATISTICS, "$key=$value"))
  }

  private fun processMessage(message: LogMessage) {
    logger.processMessage(message)
  }

  override fun reportBuildProblem(description: String, identity: String?) {
    processMessage(BuildProblemLogMessage(description = description, identity = identity))
  }

  override fun cancelBuild(reason: String) {
    logger.processMessage(LogMessage(LogMessage.Kind.BUILD_CANCEL, reason))
  }
}

/**
 * Used to print a debug-level log message to a file in the build output.
 * It firstly prints messages to a temp file and copies it to the real file after the build process cleans up the output directory.
 */
private class DebugLogger {
  private var output: Writer = StringWriter()
  private var outputFile: Path? = null
  private val loggers = ArrayList<PrintWriterBuildMessageLogger>()

  @Synchronized
  fun setOutputFile(outputFile: Path) {
    this.outputFile = outputFile
    val oldOutput = output
    oldOutput.close()
    Files.createDirectories(outputFile.parent)
    output = Files.newBufferedWriter(outputFile)
    if (oldOutput is StringWriter && oldOutput.buffer.isNotEmpty()) {
      output.write(oldOutput.buffer.toString())
    }
    for (logger in loggers) {
      logger.setOutput(output)
    }
  }

  @Synchronized
  fun close() {
    output.close()
    outputFile = null
  }

  @Synchronized
  fun getOutputFile(): Path? = outputFile

  @Synchronized
  fun createLogger(): BuildMessageLogger {
    val logger = PrintWriterBuildMessageLogger(output = output, disposer = loggers::remove)
    loggers.add(logger)
    return logger
  }
}

private class PrintWriterBuildMessageLogger(
  private var output: Writer,
  private val disposer: Consumer<PrintWriterBuildMessageLogger>,
) : BuildMessageLoggerBase() {
  @Synchronized
  fun setOutput(output: Writer) {
    this.output = output
  }

  @Override
  @Synchronized
  override fun printLine(line: String) {
    output.write(line)
    output.write('\n'.code)
    output.flush()
  }

  override fun dispose() {
    disposer.accept(this)
  }
}

@Internal
fun reportBuildProblem(description: String, identity: String? = null) {
  if (isUnderTeamCity) {
    val logger = TeamCityBuildMessageLogger()
    try {
      logger.processMessage(BuildProblemLogMessage(description, identity))
    }
    finally {
      logger.dispose()
    }
  }
  else {
    error("$identity: $description")
  }
}

private class CompositeBuildMessageLogger(private val loggers: List<BuildMessageLogger>) : BuildMessageLogger() {
  override fun processMessage(message: LogMessage) {
    for (it in loggers) {
      it.processMessage(message)
    }
  }

  override fun dispose() {
    loggers.forEach(BuildMessageLogger::dispose)
  }
}

