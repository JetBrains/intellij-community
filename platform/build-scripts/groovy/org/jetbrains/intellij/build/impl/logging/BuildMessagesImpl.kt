// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging

import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.util.containers.Stack
import org.apache.tools.ant.DefaultLogger
import org.apache.tools.ant.Project
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.LayoutBuilder
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import java.util.*

class BuildMessagesImpl private constructor(private val logger: BuildMessageLogger,
                                            private val debugLogger: DebugLogger,
                                            private val parentInstance: BuildMessagesImpl?) : BuildMessages {
  private val delayedMessages = ArrayList<LogMessage>()
  private val blockNames = Stack<String>()

  companion object {
    fun create(): BuildMessagesImpl {
      val antProject = LayoutBuilder.getAnt().project
      val key = "IntelliJBuildMessages"
      antProject.getReference<BuildMessagesImpl>(key)?.let {
        return it
      }

      val underTeamCity = System.getenv("TEAMCITY_VERSION") != null
      disableAntLogging(antProject)
      val mainLoggerFactory = if (underTeamCity) TeamCityBuildMessageLogger.FACTORY else ConsoleBuildMessageLogger.FACTORY
      val debugLogger = DebugLogger()
      val loggerFactory: (String?, AntTaskLogger) -> BuildMessageLogger = { taskName, logger ->
        CompositeBuildMessageLogger(listOf(mainLoggerFactory.apply(taskName, logger), debugLogger.createLogger(taskName)))
      }
      val antTaskLogger = AntTaskLogger(antProject)
      val messages = BuildMessagesImpl(logger = loggerFactory(null, antTaskLogger),
                                       debugLogger = debugLogger,
                                       parentInstance = null)
      antTaskLogger.defaultHandler = messages
      antProject.addBuildListener(antTaskLogger)
      antProject.addReference(key, messages)
      return messages
    }
    /**
     * default Ant logging doesn't work well with parallel tasks, so we use our own [AntTaskLogger] instead
     */
    private fun disableAntLogging(project: Project) {
      for (it in project.buildListeners) {
        if (it is DefaultLogger) {
          it.setMessageOutputLevel(Project.MSG_ERR)
        }
      }
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

  fun setDebugLogPath(path: Path?) {
    debugLogger.outputFile = path
  }

  val debugLogFile: Path
    get() = debugLogger.outputFile

  override fun error(message: String) {
    try {
      TraceManager.finish()
    }
    catch (e: Throwable) {
      System.err.println("Cannot finish tracing: $e")
    }
    throw RuntimeException(message)
  }

  override fun error(message: String, cause: Throwable) {
    val writer = StringWriter()
    PrintWriter(writer).use(cause::printStackTrace)
    processMessage(LogMessage(kind = LogMessage.Kind.ERROR, text = """
       $message
       $writer
       """.trimIndent()))
    throw RuntimeException(message, cause)
  }

  override fun compilationError(compilerName: String, message: String) {
    compilationErrors(compilerName, listOf(message))
  }

  override fun compilationErrors(compilerName: String, messages: List<String>) {
    processMessage(CompilationErrorsLogMessage(compilerName, messages))
  }

  override fun progress(message: String) {
    if (parentInstance != null) {
      //progress messages should be shown immediately, there are no problems with that since they aren't organized into groups
      parentInstance.progress(message)
    }
    else {
      logger.processMessage(LogMessage(LogMessage.Kind.PROGRESS, message))
    }
  }

  override fun buildStatus(message: String) {
    processMessage(LogMessage(LogMessage.Kind.BUILD_STATUS, message))
  }

  override fun setParameter(parameterName: String, value: String) {
    processMessage(LogMessage(LogMessage.Kind.SET_PARAMETER, "$parameterName=$value"))
  }

  override fun <V> block(blockName: String, task: () -> V): V {
    spanBuilder(blockName.lowercase(Locale.getDefault())).useWithScope {
      try {
        blockNames.push(blockName)
        processMessage(LogMessage(LogMessage.Kind.BLOCK_STARTED, blockName))
        return task()
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
    if (parentInstance == null) {
      logger.processMessage(message)
    }
    else {
      //It appears that TeamCity currently cannot properly handle log messages from parallel tasks (https://youtrack.jetbrains.com/issue/TW-46515)
      //Until it is fixed we need to delay delivering of messages from the tasks running in parallel until all tasks have been finished.
      delayedMessages.add(message)
    }
  }
}