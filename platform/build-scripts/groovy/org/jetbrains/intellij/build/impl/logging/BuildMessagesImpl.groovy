// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.logging

import com.intellij.util.containers.Stack
import com.intellij.util.text.UniqueNameGenerator
import groovy.transform.CompileStatic
import org.apache.tools.ant.BuildException
import org.apache.tools.ant.DefaultLogger
import org.apache.tools.ant.Project
import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.CompilationErrorsLogMessage
import org.jetbrains.intellij.build.LogMessage

import java.util.function.BiFunction
/**
 * @author nik
 */
@CompileStatic
class BuildMessagesImpl implements BuildMessages {
  private final BuildMessageLogger logger
  private final BiFunction<String, AntTaskLogger, BuildMessageLogger> loggerFactory
  private final AntTaskLogger antTaskLogger
  private final DebugLogger debugLogger
  private final BuildMessagesImpl parentInstance
  private final List<BuildMessagesImpl> forkedInstances = []
  private final List<LogMessage> delayedMessages = []
  private final UniqueNameGenerator taskNameGenerator = new UniqueNameGenerator()
  private final Stack<String> blockNames = new Stack<>()

  static BuildMessagesImpl create(Project antProject) {
    String key = "IntelliJBuildMessages"
    def registered = antProject.getReference(key)
    if (registered != null) return registered as BuildMessagesImpl

    boolean underTeamCity = System.getProperty("teamcity.buildType.id") != null
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

  void setDebugLogPath(String path) {
    debugLogger.setOutputFile(new File(path))
  }

  @Override
  void error(String message) {
    throw new BuildException(message)
  }

  @Override
  void error(String message, Throwable cause) {
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
  <V> V block(String blockName, Closure<V> body) {
    try {
      blockNames.push(blockName)
      processMessage(new LogMessage(LogMessage.Kind.BLOCK_STARTED, blockName))
      return body()
    }
    catch (IntelliJBuildException e) {
      throw e
    }
    catch (BuildException e) {
      throw new IntelliJBuildException(blockNames.join(" > "), e.message, e.cause)
    }
    finally {
      blockNames.pop()
      processMessage(new LogMessage(LogMessage.Kind.BLOCK_FINISHED, blockName))
    }
  }

  @Override
  void artifactBuilt(String relativeArtifactPath) {
    processMessage(new LogMessage(LogMessage.Kind.ARTIFACT_BUILT, relativeArtifactPath))
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