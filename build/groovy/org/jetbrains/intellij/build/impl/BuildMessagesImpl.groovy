/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.apache.tools.ant.BuildException
import org.apache.tools.ant.DefaultLogger
import org.apache.tools.ant.Project
import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.LogMessage
import org.jetbrains.jps.gant.BuildInfoPrinter
import org.jetbrains.jps.gant.DefaultBuildInfoPrinter
import org.jetbrains.jps.gant.JpsGantProjectBuilder
import org.jetbrains.jps.gant.TeamCityBuildInfoPrinter

import java.util.function.Function

/**
 * @author nik
 */
@CompileStatic
class BuildMessagesImpl implements BuildMessages {
  private final BuildMessageLogger logger
  private final Function<String, BuildMessageLogger> loggerFactory
  private final AntTaskLogger antTaskLogger
  private final BuildMessagesImpl parentInstance
  private final List<BuildMessagesImpl> forkedInstances = []
  private final List<LogMessage> delayedMessages = []

  static BuildMessagesImpl create(JpsGantProjectBuilder builder, Project antProject, boolean underTeamCity) {
    String key = "IntelliJBuildMessages"
    def registered = antProject.getReference(key)
    if (registered != null) return registered as BuildMessagesImpl

    BuildInfoPrinter buildInfoPrinter = underTeamCity ? new TeamCityBuildInfoPrinter() : new DefaultBuildInfoPrinter()
    builder.buildInfoPrinter = buildInfoPrinter
    disableAntLogging(antProject)
    Function<String, BuildMessageLogger> loggerFactory = underTeamCity ? TeamCityBuildMessageLogger.FACTORY : ConsoleBuildMessageLogger.FACTORY
    def antTaskLogger = new AntTaskLogger()
    def messages = new BuildMessagesImpl(loggerFactory.apply(null), loggerFactory, antTaskLogger, null)
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

  private BuildMessagesImpl(BuildMessageLogger logger, Function<String, BuildMessageLogger> loggerFactory, AntTaskLogger antTaskLogger,
                            BuildMessagesImpl parentInstance) {
    this.logger = logger
    this.loggerFactory = loggerFactory
    this.antTaskLogger = antTaskLogger
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
  void error(String message) {
    throw new BuildException(message)
  }

  @Override
  void error(String message, Throwable cause) {
    throw new BuildException(message, cause)
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
  public <V> V block(String blockName, Closure<V> body) {
    try {
      processMessage(new LogMessage(LogMessage.Kind.BLOCK_STARTED, blockName))
      return body()
    }
    finally {
      processMessage(new LogMessage(LogMessage.Kind.BLOCK_FINISHED, blockName))
    }
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
  BuildMessages forkForParallelTask(String taskName) {
    def forked = new BuildMessagesImpl(loggerFactory.apply(taskName), loggerFactory, antTaskLogger, this)
    forkedInstances << forked
    return forked
  }

  @Override
  void onAllForksFinished() {
    forkedInstances.each { forked ->
      forked.delayedMessages.each {
        forked.logger.processMessage(it)
      }
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