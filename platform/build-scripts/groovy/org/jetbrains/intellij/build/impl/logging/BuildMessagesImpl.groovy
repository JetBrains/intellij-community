// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.logging

import com.intellij.util.containers.Stack
import com.intellij.util.text.UniqueNameGenerator
import groovy.transform.CompileStatic
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Scope
import io.opentelemetry.sdk.trace.ReadableSpan
import org.apache.tools.ant.BuildException
import org.apache.tools.ant.DefaultLogger
import org.apache.tools.ant.Project
import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.LogMessage
import org.jetbrains.intellij.build.impl.TracerManager
import org.jetbrains.intellij.build.impl.TracerProviderManager

import java.lang.reflect.UndeclaredThrowableException
import java.nio.file.Path
import java.util.function.BiFunction
import java.util.function.Supplier

@CompileStatic
final class BuildMessagesImpl implements BuildMessages {
  private final BuildMessageLogger logger
  private final BiFunction<String, AntTaskLogger, BuildMessageLogger> loggerFactory
  private final AntTaskLogger antTaskLogger
  private final DebugLogger debugLogger
  private final BuildMessagesImpl parentInstance
  private final List<BuildMessagesImpl> forkedInstances = []
  private final List<LogMessage> delayedMessages = []
  private final UniqueNameGenerator taskNameGenerator = new UniqueNameGenerator()
  private final Stack<String> blockNames = new Stack<>()

  @Override
  String getName() {
    return ""
  }

  @Override
  boolean isLoggable(System.Logger.Level level) {
    return level.severity > System.Logger.Level.TRACE.severity
  }

  @Override
  void log(System.Logger.Level level, ResourceBundle bundle, String message, Throwable thrown) {
    if (level == System.Logger.Level.INFO) {
      assert thrown == null
      info(message)
    }
    else if (level == System.Logger.Level.ERROR) {
      error(message, thrown)
    }
    else if (level == System.Logger.Level.WARNING) {
      assert thrown == null
      warning(message)
    }
    else {
      assert thrown == null
      debug(message)
    }
  }

  @Override
  void log(System.Logger.Level level, ResourceBundle bundle, String message, Object... params) {
    log(level, null, message, null as Throwable)
  }

  @Override
  void log(System.Logger.Level level, String message, Object... params) {
    log(level, null, message, null as Throwable)
  }

  @Override
  void log(System.Logger.Level level, String message) {
    log(level, null, message, null as Throwable)
  }

  @Override
  void log(System.Logger.Level level, String message, Throwable thrown) {
    log(level, null, message, thrown)
  }

  static BuildMessagesImpl create(Project antProject) {
    String key = "IntelliJBuildMessages"
    def registered = antProject.getReference(key)
    if (registered != null) return registered as BuildMessagesImpl

    boolean underTeamCity = System.getenv("TEAMCITY_VERSION") != null
    disableAntLogging(antProject)
    BiFunction<String, AntTaskLogger, BuildMessageLogger> mainLoggerFactory = underTeamCity ? TeamCityBuildMessageLogger.FACTORY : ConsoleBuildMessageLogger.FACTORY
    def debugLogger = new DebugLogger()
    BiFunction<String, AntTaskLogger, BuildMessageLogger> loggerFactory = { String taskName, AntTaskLogger logger ->
      new CompositeBuildMessageLogger([mainLoggerFactory.apply(taskName, logger), debugLogger.createLogger(taskName)])
    } as BiFunction<String, AntTaskLogger, BuildMessageLogger>
    def antTaskLogger = new AntTaskLogger(antProject)
    def messages = new BuildMessagesImpl(loggerFactory.apply(null, antTaskLogger), loggerFactory, antTaskLogger, debugLogger, null)
    antTaskLogger.defaultHandler = messages
    antProject.addBuildListener(antTaskLogger)
    antProject.addReference(key, messages)
    return messages
  }

  /**
   * default Ant logging doesn't work well with parallel tasks, so we use our own {@link AntTaskLogger} instead
   */
  private static void disableAntLogging(Project project) {
    project.getBuildListeners().each {
      if (it instanceof DefaultLogger) {
        it.setMessageOutputLevel(Project.MSG_ERR)
      }
    }
  }

  private BuildMessagesImpl(BuildMessageLogger logger, BiFunction<String, AntTaskLogger, BuildMessageLogger> loggerFactory, AntTaskLogger antTaskLogger,
                            DebugLogger debugLogger, BuildMessagesImpl parentInstance) {
    this.logger = logger
    this.loggerFactory = loggerFactory
    this.antTaskLogger = antTaskLogger
    this.debugLogger = debugLogger
    this.parentInstance = parentInstance
  }

  @Override
  void info(String message) {
    processMessage(new LogMessage(LogMessage.Kind.INFO, message))
  }

  @Override
  void warning(String message) {
    processMessage(new LogMessage(LogMessage.Kind.WARNING, message))
  }

  @Override
  void debug(String message) {
    processMessage(new LogMessage(LogMessage.Kind.DEBUG, message))
  }

  void setDebugLogPath(Path path) {
    debugLogger.setOutputFile(path)
  }

  Path getDebugLogFile() {
    debugLogger.getOutputFile()
  }

  @Override
  void error(String message) {
    try {
      TracerManager.finish()
    }
    catch (Throwable e) {
      System.err.println("Cannot finish tracing: " + e)
    }
    throw new BuildException(message)
  }

  @Override
  void error(String message, Throwable cause) {
    def writer = new StringWriter()
    new PrintWriter(writer).withCloseable { cause?.printStackTrace(it) }
    processMessage(new LogMessage(LogMessage.Kind.ERROR, "$message\n$writer"))
    throw new BuildException(message, cause)
  }

  @Override
  void compilationError(String compilerName, String message) {
    compilationErrors(compilerName, [message])
  }

  @Override
  void compilationErrors(String compilerName, List<String> messages) {
    processMessage(new CompilationErrorsLogMessage(compilerName, messages))
  }

  @Override
  void progress(String message) {
    if (parentInstance != null) {
      //progress messages should be shown immediately, there are no problems with that since they aren't organized into groups
      parentInstance.progress(message)
    }
    else {
      logger.processMessage(new LogMessage(LogMessage.Kind.PROGRESS, message))
    }
  }

  @Override
  void buildStatus(String message) {
    processMessage(new LogMessage(LogMessage.Kind.BUILD_STATUS, message))
  }

  @Override
  void setParameter(String parameterName, String value) {
    processMessage(new LogMessage(LogMessage.Kind.SET_PARAMETER, "$parameterName=$value"))
  }

  @Override
  <V> V block(String blockName, Supplier<V> body) {
    block(TracerManager.spanBuilder(blockName.toLowerCase()), body)
  }

  @Override
  <V> V block(SpanBuilder spanBuilder, Supplier<V> body) {
    Span span = spanBuilder.startSpan()
    Scope scope = span.makeCurrent()
    String blockName = ((ReadableSpan)span).getName()
    try {
      blockNames.push(blockName)
      processMessage(new LogMessage(LogMessage.Kind.BLOCK_STARTED, blockName))
      return body.get()
    }
    catch (IntelliJBuildException e) {
      span.setStatus(StatusCode.ERROR, e.message)
      span.recordException(e)
      throw e
    }
    catch (BuildException e) {
      span.setStatus(StatusCode.ERROR, e.message)
      span.recordException(e)
      throw new IntelliJBuildException(blockNames.join(" > "), e.message, e)
    }
    catch (Throwable e) {
      if (e instanceof UndeclaredThrowableException) {
        e = e.cause
      }

      span.recordException(e)
      span.setStatus(StatusCode.ERROR, e.message)

      // print all pending spans
      TracerProviderManager.flush()
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
      processMessage(new LogMessage(LogMessage.Kind.BLOCK_FINISHED, blockName))
    }
  }

  @Override
  void artifactBuilt(String relativeArtifactPath) {
    logger.processMessage(new LogMessage(LogMessage.Kind.ARTIFACT_BUILT, relativeArtifactPath))
  }

  @Override
  void reportStatisticValue(String key, String value) {
    processMessage(new LogMessage(LogMessage.Kind.STATISTICS, "$key=$value"))
  }

  void processMessage(LogMessage message) {
    if (parentInstance != null) {
      //It appears that TeamCity currently cannot properly handle log messages from parallel tasks (https://youtrack.jetbrains.com/issue/TW-46515)
      //Until it is fixed we need to delay delivering of messages from the tasks running in parallel until all tasks have been finished.
      delayedMessages.add(message)
    }
    else {
      logger.processMessage(message)
    }
  }

  @Override
  BuildMessages forkForParallelTask(String suggestedTaskName) {
    String taskName = taskNameGenerator.generateUniqueName(suggestedTaskName)
    def forked = new BuildMessagesImpl(loggerFactory.apply(taskName, antTaskLogger), loggerFactory, antTaskLogger, debugLogger, this)
    forkedInstances << forked
    return forked
  }

  @Override
  void onAllForksFinished() {
    forkedInstances.each { forked ->
      forked.delayedMessages.each {
        forked.logger.processMessage(it)
      }
      forked.logger.dispose()
    }
    forkedInstances.clear()
  }

  @Override
  void onForkStarted() {
    antTaskLogger.registerThreadHandler(Thread.currentThread(), this)
  }

  @Override
  void onForkFinished() {
    antTaskLogger.unregisterThreadHandler(Thread.currentThread())
  }
}