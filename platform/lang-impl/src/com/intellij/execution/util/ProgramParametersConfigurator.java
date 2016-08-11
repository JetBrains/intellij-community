/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution.util;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.configurations.SimpleProgramParameters;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ProgramParametersConfigurator {
  public void configureConfiguration(SimpleProgramParameters parameters, CommonProgramRunConfigurationParameters configuration) {
    Project project = configuration.getProject();
    Module module = getModule(configuration);

    parameters.getProgramParametersList().addParametersString(expandPath(configuration.getProgramParameters(), module, project));

    parameters.setWorkingDirectory(getWorkingDir(configuration, project, module));

    Map<String, String> envs = new HashMap<>(configuration.getEnvs());
    EnvironmentUtil.inlineParentOccurrences(envs);
    for (Map.Entry<String, String> each : envs.entrySet()) {
      each.setValue(expandPath(each.getValue(), module, project));
    }
    
    parameters.setEnv(envs);
    parameters.setPassParentEnvs(configuration.isPassParentEnvs());
  }

  @Nullable
  public String getWorkingDir(CommonProgramRunConfigurationParameters configuration, Project project, Module module) {
    String workingDirectory = configuration.getWorkingDirectory();
    String defaultWorkingDir = getDefaultWorkingDir(project);
    if (StringUtil.isEmptyOrSpaces(workingDirectory)) {
      workingDirectory = defaultWorkingDir;
      if (workingDirectory == null) {
        return null;
      }
    }
    workingDirectory = expandPath(workingDirectory, module, project);
    if (!FileUtil.isAbsolute(workingDirectory) && defaultWorkingDir != null) {
      if (("$" + PathMacroUtil.MODULE_DIR_MACRO_NAME + "$").equals(workingDirectory)) {
        return defaultWorkingDir;
      }
      workingDirectory = defaultWorkingDir + "/" + workingDirectory;
    }
    return workingDirectory;
  }

  @Nullable
  protected String getDefaultWorkingDir(@NotNull Project project) {
    return PathUtil.getLocalPath(project.getBaseDir());
  }

  public void checkWorkingDirectoryExist(CommonProgramRunConfigurationParameters configuration, Project project, Module module)
    throws RuntimeConfigurationWarning {
    final String workingDir = getWorkingDir(configuration, project, module);
    if (workingDir == null) {
      throw new RuntimeConfigurationWarning("Working directory is null for "+
                                            "project '" + project.getName() + "' ("+project.getBasePath()+")"
                                            + ", module '" + module.getName() + "' (" + module.getModuleFilePath() + ")");
    }
    if (!new File(workingDir).exists()) {
      throw new RuntimeConfigurationWarning("Working directory '" + workingDir + "' doesn't exist");
    }
  }

  protected String expandPath(String path, Module module, Project project) {
    path = PathMacroManager.getInstance(project).expandPath(path);
    if (module != null) {
      path = PathMacroManager.getInstance(module).expandPath(path);
    }
    return path;
  }

  @Nullable
  protected Module getModule(CommonProgramRunConfigurationParameters configuration) {
    if (configuration instanceof ModuleBasedConfiguration) {
      return ((ModuleBasedConfiguration)configuration).getConfigurationModule().getModule();
    }
    return null;
  }
}
