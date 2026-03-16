// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging

import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildScriptsLoggedError
import org.jetbrains.intellij.build.dependencies.TeamCityHelper.isUnderTeamCity
import org.jetbrains.intellij.build.logging.BuildMessageLogger
import org.jetbrains.intellij.build.logging.BuildMessageLoggerBase
import org.jetbrains.intellij.build.logging.BuildProblemLogMessage
import org.jetbrains.intellij.build.logging.CompilationErrorsLogMessage
import org.jetbrains.intellij.build.logging.ConsoleBuildMessageLogger
import org.jetbrains.intellij.build.logging.LogMessage
import org.jetbrains.intellij.build.logging.TeamCityBuildMessageLogger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.function.Consumer

class BuildMessagesImpl private constructor(
  private val logger: BuildMessageLogger,
  private val debugLogger: DebugLogger,
) : BuildMessages {
  companion object {
    fun create(): BuildMessagesImpl {
      val mainLoggerFactory = if (isUnderTeamCity) ::TeamCityBuildMessageLogger else ::ConsoleBuildMessageLogger
      val debugLogger = DebugLogger()
      return BuildMessagesImpl(
        logger = CompositeBuildMessageLogger(listOf(mainLoggerFactory(), debugLogger.createLogger())),
        debugLogger = debugLogger,
      )
    }
  }

  override fun info(message: String) {
    processMessage(LogMessage(LogMessage.Kind.INFO, message))
  }

  override fun warning(message: String) {
    processMessage(LogMessage(LogMessage.Kind.WARNING, message))
  }

  override fun debug(message: String) {
    processMessage(LogMessage(LogMessage.Kind.DEBUG, message))
  }

  fun setDebugLogPath(path: Path) {
    System.err.println("Debug logging (but not spans) will be written to $path")
    debugLogger.setOutputFile(path)
  }

  override fun close() {
    debugLogger.close()
  }

  override fun getDebugLog(): String = debugLogger.getOutput()

  override fun logErrorAndThrow(message: String) {
    errorImpl(message, cause = null)
  }

  override fun logErrorAndThrow(message: String, cause: Throwable) {
    errorImpl(message, cause)
  }

  private fun errorImpl(message: String, cause: Throwable? = null) {
    processMessage(
      LogMessage(
        kind = LogMessage.Kind.ERROR,
        text = message + if (cause == null) "" else "\n${cause.stackTraceToString()}",
      )
    )
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

  @Suppress("OVERRIDE_DEPRECATION")
  override fun block(blockName: String, task: Callable<Unit>) {
    try {
      processMessage(LogMessage(LogMessage.Kind.BLOCK_STARTED, blockName))
      task.call()
    }
    finally {
      processMessage(LogMessage(LogMessage.Kind.BLOCK_FINISHED, blockName))
    }
  }

  override fun artifactBuilt(relativeArtifactPath: String) {
    logger.processMessage(LogMessage(LogMessage.Kind.ARTIFACT_BUILT, relativeArtifactPath))
  }

  override fun startWritingFileToBuildLog(artifactPath: String) {
    logger.processMessage(LogMessage(LogMessage.Kind.IMPORT_DATA, artifactPath))
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

  override fun reportBuildNumber(value: String) {
    logger.processMessage(LogMessage(LogMessage.Kind.BUILD_NUMBER, value))
  }
}

/**
 * Used to print a debug-level log message to a file in the build output.
 * It first prints messages to a temp file and copies it to the real file after the build process cleans up the output directory.
 */
private class DebugLogger {
  // Most of the logging is carried out via OpenTelemetry. Thus, the debug log is commonly empty.
  private val output = StringBuilder()
  private var outputFile: Path? = null
  private val loggers = ArrayList<PrintWriterBuildMessageLogger>()

  @Synchronized
  fun setOutputFile(outputFile: Path) {
    this.outputFile = outputFile
  }

  @Synchronized
  fun close() {
    val file = outputFile ?: return
    if (output.isNotEmpty()) {
      Files.createDirectories(file.parent)
      Files.writeString(file, output.toString())
    }

    outputFile = null
  }

  @Synchronized
  fun getOutput(): String = output.toString()

  @Synchronized
  fun createLogger(): BuildMessageLogger {
    val logger = PrintWriterBuildMessageLogger(output = output, disposer = loggers::remove)
    loggers.add(logger)
    return logger
  }
}

/**
 * Used unconditionally by both [TeamCityBuildMessageLogger] and [ConsoleBuildMessageLogger]
 */
private class PrintWriterBuildMessageLogger(
  private val output: StringBuilder,
  private val disposer: Consumer<PrintWriterBuildMessageLogger>,
) : BuildMessageLoggerBase() {
  @Override
  @Synchronized
  override fun printLine(line: String) {
    output.append(line)
    output.append('\n'.code)
  }

  override fun dispose() {
    disposer.accept(this)
  }
}

internal fun reportBuildProblem(description: String, identity: String? = null) {
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

