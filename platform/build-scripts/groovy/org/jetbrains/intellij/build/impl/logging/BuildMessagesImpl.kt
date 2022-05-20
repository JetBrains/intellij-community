// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging

import com.intellij.util.containers.Stack
import com.intellij.util.text.UniqueNameGenerator
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.ReadableSpan
import org.apache.tools.ant.BuildException
import org.apache.tools.ant.DefaultLogger
import org.apache.tools.ant.Project
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.finish
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.TracerProviderManager.flush
import org.jetbrains.intellij.build.impl.LayoutBuilder
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import java.util.*
import java.util.function.BiFunction

class BuildMessagesImpl private constructor(private val logger: BuildMessageLogger,
                                            private val loggerFactory: BiFunction<String?, AntTaskLogger, BuildMessageLogger>,
                                            private val antTaskLogger: AntTaskLogger,
                                            private val debugLogger: DebugLogger,
                                            private val parentInstance: BuildMessagesImpl?) : BuildMessages {
  private val forkedInstances = ArrayList<BuildMessagesImpl>()
  private val delayedMessages = ArrayList<LogMessage>()
  private val taskNameGenerator = UniqueNameGenerator()
  private val blockNames = Stack<String>()

  companion object {
    fun create(): BuildMessagesImpl {
      val antProject = LayoutBuilder.getAnt().project
      val key = "IntelliJBuildMessages"
      val registered = antProject.getReference<Any>(key)
      if (registered != null) return DefaultGroovyMethods.asType(registered, BuildMessagesImpl::class.java)
      val underTeamCity = System.getenv("TEAMCITY_VERSION") != null
      disableAntLogging(antProject)
      val mainLoggerFactory = if (underTeamCity) {
        TeamCityBuildMessageLogger.FACTORY
      }
      else {
        ConsoleBuildMessageLogger.FACTORY
      }
      val debugLogger = DebugLogger()
      val loggerFactory = BiFunction<String?, AntTaskLogger, BuildMessageLogger> { taskName: String?, logger: AntTaskLogger ->
        CompositeBuildMessageLogger(java.util.List.of(mainLoggerFactory.apply(taskName, logger), debugLogger.createLogger(taskName)))
      }
      val antTaskLogger = AntTaskLogger(antProject)
      val messages = BuildMessagesImpl(loggerFactory.apply(null, antTaskLogger), loggerFactory, antTaskLogger, debugLogger, null)
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
      System.Logger.Level.INFO -> {
        info(message)
      }
      System.Logger.Level.ERROR -> {
        error(message, thrown!!)
      }
      System.Logger.Level.WARNING -> {
        warning(message)
      }
      else -> {
        debug(message)
      }
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
      finish()
    }
    catch (e: Throwable) {
      System.err.println("Cannot finish tracing: $e")
    }
    throw BuildException(message)
  }

  override fun error(message: String, cause: Throwable) {
    val writer = StringWriter()
    PrintWriter(writer).use { it -> cause.printStackTrace(it) }
    processMessage(LogMessage(LogMessage.Kind.ERROR, """
   $message
   $writer
   """.trimIndent()))
    throw BuildException(message, cause)
  }

  override fun compilationError(compilerName: String, message: String) {
    compilationErrors(compilerName, ArrayList(Arrays.asList(message)))
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
    return block(spanBuilder(blockName.lowercase(Locale.getDefault())), task)
  }

  override fun <V> block(spanBuilder: SpanBuilder, task: () -> V): V {
    val span = spanBuilder.startSpan()
    val scope = span.makeCurrent()
    val blockName = (span as ReadableSpan).name
    return try {
      blockNames.push(blockName)
      processMessage(LogMessage(LogMessage.Kind.BLOCK_STARTED, blockName))
      task()
    }
    catch (e: IntelliJBuildException) {
      span.setStatus(StatusCode.ERROR, e.message!!)
      span.recordException(e)
      throw e
    }
    catch (e: BuildException) {
      span.setStatus(StatusCode.ERROR, e.message!!)
      span.recordException(e)
      throw IntelliJBuildException(blockNames.joinToString(separator = " > "), e.message!!, e)
    }
    catch (e: Throwable) {
      span.recordException(e)
      span.setStatus(StatusCode.ERROR, e.message!!)

      // print all pending spans
      flush()
      throw e
    }
    finally {
      try {
        scope.close()
      }
      finally {
        span.end()
      }
      blockNames.pop()
      processMessage(LogMessage(LogMessage.Kind.BLOCK_FINISHED, blockName))
    }
  }

  override fun artifactBuilt(relativeArtifactPath: String) {
    logger.processMessage(LogMessage(LogMessage.Kind.ARTIFACT_BUILT, relativeArtifactPath))
  }

  override fun reportStatisticValue(key: String, value: String) {
    processMessage(LogMessage(LogMessage.Kind.STATISTICS, "$key=$value"))
  }

  fun processMessage(message: LogMessage) {
    if (parentInstance != null) {
      //It appears that TeamCity currently cannot properly handle log messages from parallel tasks (https://youtrack.jetbrains.com/issue/TW-46515)
      //Until it is fixed we need to delay delivering of messages from the tasks running in parallel until all tasks have been finished.
      delayedMessages.add(message)
    }
    else {
      logger.processMessage(message)
    }
  }

  override fun forkForParallelTask(taskName: String): BuildMessages {
    val forked = BuildMessagesImpl(loggerFactory.apply(taskNameGenerator.generateUniqueName(taskName), antTaskLogger), loggerFactory, antTaskLogger, debugLogger, this)
    DefaultGroovyMethods.leftShift(forkedInstances, forked)
    return forked
  }

  override fun onAllForksFinished() {
    for (forked in forkedInstances) {
      for (message in forked.delayedMessages) {
        forked.logger.processMessage(message)
      }
      forked.logger.dispose()
    }
    forkedInstances.clear()
  }

  override fun onForkStarted() {
    antTaskLogger.registerThreadHandler(Thread.currentThread(), this)
  }

  override fun onForkFinished() {
    antTaskLogger.unregisterThreadHandler(Thread.currentThread())
  }
}