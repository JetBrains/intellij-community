// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging

import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.containers.Stack
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import java.io.BufferedWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.function.Consumer

class BuildMessagesImpl private constructor(private val logger: BuildMessageLogger,
                                            private val debugLogger: DebugLogger) : BuildMessages {
  private val blockNames = Stack<String>()

  companion object {
    fun create(): BuildMessagesImpl {
      val underTeamCity = System.getenv("TEAMCITY_VERSION") != null
      val mainLoggerFactory = if (underTeamCity) TeamCityBuildMessageLogger.FACTORY else ConsoleBuildMessageLogger.FACTORY
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
    log(level = level, message = format, bundle  = bundle, thrown = null)
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
    debugLogger.setOutputFile(path)
  }

  fun close() {
    debugLogger.close()
  }

  val debugLogFile: Path?
    get() = debugLogger.getOutputFile()

  override fun error(message: String) {
    try {
      TraceManager.finish()
    }
    catch (e: Throwable) {
      System.err.println("Cannot finish tracing: $e")
    }
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

  override fun setParameter(parameterName: String, value: String) {
    processMessage(LogMessage(LogMessage.Kind.SET_PARAMETER, "$parameterName=$value"))
  }

  override fun <V> block(blockName: String, task: ThrowableComputable<V, Exception>): V {
    spanBuilder(blockName.lowercase(Locale.getDefault())).useWithScope {
      try {
        blockNames.push(blockName)
        processMessage(LogMessage(LogMessage.Kind.BLOCK_STARTED, blockName))
        return task.compute()
      }
      catch (e: Throwable) {
        // print all pending spans
        TracerProviderManager.flush()
        throw e
      }
      finally {
        blockNames.pop()
        processMessage(LogMessage(LogMessage.Kind.BLOCK_FINISHED, blockName))
      }
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
}

/**
 * Used to print debug-level log message to a file in the build output. It firstly prints messages to a temp file and copies it to the real
 * file after the build process cleans up the output directory.
 */
private class DebugLogger {
  private val tempFile: Path = Files.createTempFile("intellij-build", ".log")
  private var output: BufferedWriter
  private var outputFile: Path? = null
  private val loggers = ArrayList<PrintWriterBuildMessageLogger>()

  init {
    Files.createDirectories(tempFile.parent)
    output = Files.newBufferedWriter(tempFile)
  }

  @Synchronized
  fun setOutputFile(outputFile: Path) {
    this.outputFile = outputFile
    output.close()
    Files.createDirectories(outputFile.parent)
    if (Files.exists(tempFile)) {
      Files.move(tempFile, outputFile, StandardCopyOption.REPLACE_EXISTING)
    }
    output = Files.newBufferedWriter(outputFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
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
  private var output: BufferedWriter,
  private val disposer: Consumer<PrintWriterBuildMessageLogger>,
) : BuildMessageLoggerBase() {
  @Synchronized
  fun setOutput(output: BufferedWriter) {
    this.output = output
  }

  @Override
  @Synchronized
  override fun printLine(line: String) {
    output.write(line)
    output.write("\n")
    output.flush()
  }

  override fun dispose() {
    disposer.accept(this)
  }
}
