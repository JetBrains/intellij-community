/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.WorkingDirectoryProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ProgramParametersConfigurator {
  private static final ExtensionPointName<WorkingDirectoryProvider> WORKING_DIRECTORY_PROVIDER_EP_NAME= ExtensionPointName
    .create("com.intellij.module.workingDirectoryProvider");
  public static final String MODULE_WORKING_DIR = "%MODULE_WORKING_DIR%";

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

      if (module != null && MODULE_WORKING_DIR.equals(workingDirectory)) {
        String workingDir = getDefaultWorkingDir(module);
        if (workingDir != null) return workingDir;
      }
      workingDirectory = defaultWorkingDir + "/" + workingDirectory;
    }
    return workingDirectory;
  }

  @Nullable
  protected String getDefaultWorkingDir(@NotNull Project project) {
    return PathUtil.getLocalPath(project.getBaseDir());
  }

  @Nullable
  protected String getDefaultWorkingDir(@NotNull Module module) {
    for (WorkingDirectoryProvider provider : WORKING_DIRECTORY_PROVIDER_EP_NAME.getExtensions()) {
      @SystemIndependent String path = provider.getWorkingDirectoryPath(module);
      if (path != null) return path;
    }
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length > 0) {
      return PathUtil.getLocalPath(roots[0]);
    }
    return null;
  }

  public void checkWorkingDirectoryExist(CommonProgramRunConfigurationParameters configuration, Project project, Module module)
    throws RuntimeConfigurationWarning {
    final String workingDir = getWorkingDir(configuration, project, module);
    if (workingDir == null) {
      throw new RuntimeConfigurationWarning("Working directory is null for "+
                                            "project '" + project.getName() + "' ("+project.getBasePath()+")"
                                            + ", module " + (module == null? "null" : "'" + module.getName() + "' (" + module.getModuleFilePath() + ")"));
    }
    if (!new File(workingDir).exists()) {
      throw new RuntimeConfigurationWarning("Working directory '" + workingDir + "' doesn't exist");
    }
  }

  protected String expandPath(@Nullable String path, Module module, Project project) {
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
