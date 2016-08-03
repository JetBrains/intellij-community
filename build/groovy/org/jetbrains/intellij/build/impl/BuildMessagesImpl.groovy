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

import org.apache.tools.ant.BuildException
import org.apache.tools.ant.DefaultLogger
import org.apache.tools.ant.Project
import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.jps.gant.BuildInfoPrinter
import org.jetbrains.jps.gant.DefaultBuildInfoPrinter
import org.jetbrains.jps.gant.JpsGantProjectBuilder
import org.jetbrains.jps.gant.TeamCityBuildInfoPrinter

import java.util.function.Function

/**
 * @author nik
 */
class BuildMessagesImpl implements BuildMessages {
  private final BuildMessageLogger logger
  private final Function<String, BuildMessageLogger> loggerFactory
  private final AntTaskLogger antTaskLogger

  static BuildMessagesImpl create(JpsGantProjectBuilder builder, Project antProject, boolean underTeamCity) {
    String key = "IntelliJBuildMessages"
    def registered = antProject.getReference(key)
    if (registered != null) return registered as BuildMessagesImpl

    BuildInfoPrinter buildInfoPrinter = underTeamCity ? new TeamCityBuildInfoPrinter() : new DefaultBuildInfoPrinter()
    builder.buildInfoPrinter = buildInfoPrinter
    disableAntLogging(antProject)
    Function<String, BuildMessageLogger> loggerFactory = underTeamCity ? TeamCityBuildMessageLogger.FACTORY : ConsoleBuildMessageLogger.FACTORY
    def antTaskLogger = new AntTaskLogger()
    def messages = new BuildMessagesImpl(loggerFactory.apply(null), loggerFactory, antTaskLogger)
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

  private BuildMessagesImpl(BuildMessageLogger logger, Function<String, BuildMessageLogger> loggerFactory, AntTaskLogger antTaskLogger) {
    this.logger = logger
    this.loggerFactory = loggerFactory
    this.antTaskLogger = antTaskLogger
  }

  @Override
  void info(String message) {
    logger.logMessage(message, BuildMessageLogger.Level.INFO)
  }

  @Override
  void warning(String message) {
    logger.logMessage(message, BuildMessageLogger.Level.WARNING)
  }

  @Override
  void error(String message) {
    throw new BuildException(message)
  }

  @Override
  void progress(String message) {
    logger.logProgressMessage(message)
  }

  @Override
  public <V> V block(String blockName, Closure<V> body) {
    try {
      logger.startBlock(blockName)
      return body()
    }
    finally {
      logger.finishBlock(blockName)
    }
  }

  @Override
  BuildMessages forkForParallelTask(String taskName) {
    return new BuildMessagesImpl(loggerFactory.apply(taskName), loggerFactory, antTaskLogger)
  }

  @Override
  void startFork() {
    antTaskLogger.registerThreadHandler(Thread.currentThread(), this)
  }

  @Override
  void finishFork() {
    antTaskLogger.unregisterThreadHandler(Thread.currentThread())
  }
}