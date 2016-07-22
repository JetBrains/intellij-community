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

import com.intellij.openapi.util.text.StringUtil
import org.apache.tools.ant.BuildException
import org.apache.tools.ant.Project
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.jps.gant.BuildInfoPrinter
import org.jetbrains.jps.gant.DefaultBuildInfoPrinter
import org.jetbrains.jps.gant.JpsGantProjectBuilder
import org.jetbrains.jps.gant.TeamCityBuildInfoPrinter

/**
 * @author nik
 */
class BuildMessagesImpl implements BuildMessages {
  private final JpsGantProjectBuilder builder
  private final BuildInfoPrinter buildInfoPrinter
  private final Project antProject
  private final boolean underTeamCity
  private int indent = 0

  BuildMessagesImpl(JpsGantProjectBuilder builder, Project antProject, boolean underTeamCity) {
    this.underTeamCity = underTeamCity
    this.antProject = antProject
    this.builder = builder
    buildInfoPrinter = underTeamCity ? new TeamCityBuildInfoPrinter() : new DefaultBuildInfoPrinter()
    builder.buildInfoPrinter = buildInfoPrinter
  }

  @Override
  void info(String message) {
    antProject.log(withIndent(message), Project.MSG_INFO)
  }

  private String withIndent(String message) {
    StringUtil.repeat(" ", 2 * indent) + message
  }

  @Override
  void warning(String message) {
    antProject.log(withIndent(message), Project.MSG_WARN)
  }

  @Override
  void error(String message) {
    throw new BuildException(message)
  }

  @Override
  void progress(String message) {
    if (underTeamCity) {
      buildInfoPrinter.printProgressMessage(builder, message)
    }
    else {
      info(message)
    }
  }

  @Override
  public <V> V block(String blockName, Closure<V> body) {
    try {
      //todo[nik] move this logic into DefaultBuildInfoPrinter?
      if (underTeamCity) {
        buildInfoPrinter.printBlockOpenedMessage(builder, blockName)
      }
      else {
        info(blockName)
        indent++
      }
      return body()
    }
    finally {
      if (underTeamCity) {
        buildInfoPrinter.printBlockClosedMessage(builder, blockName)
      }
      else {
        indent--
      }
    }
  }
}