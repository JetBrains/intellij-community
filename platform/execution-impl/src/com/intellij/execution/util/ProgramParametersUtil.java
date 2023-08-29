// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.util;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.EnvFilesOptions;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.configurations.SimpleProgramParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public final class ProgramParametersUtil {
  public static void configureConfiguration(SimpleProgramParameters parameters, CommonProgramRunConfigurationParameters configuration) {
    new ProgramParametersConfigurator().configureConfiguration(parameters, configuration);
  }

  public static String getWorkingDir(CommonProgramRunConfigurationParameters configuration, Project project, Module module) {
    return new ProgramParametersConfigurator().getWorkingDir(configuration, project, module);
  }

  public static void checkWorkingDirectoryExist(CommonProgramRunConfigurationParameters configuration, Project project, Module module)
    throws RuntimeConfigurationWarning {
    ProgramParametersConfigurator configurator = new ProgramParametersConfigurator();
    configurator.setValidation(true);
    try {
      configurator.checkWorkingDirectoryExist(configuration, project, module);
    }
    catch (IncorrectOperationException ignore) {
    }
  }

  public static void checkEnvFiles(CommonProgramRunConfigurationParameters configuration) throws RuntimeConfigurationException {
    if (configuration instanceof EnvFilesOptions) {
      try {
        ProgramParametersConfigurator.configureEnvsFromFiles((EnvFilesOptions)configuration);
      }
      catch (ProgramParametersConfigurator.ParametersConfiguratorException e) {
        throw new RuntimeConfigurationException(e.getMessage());
      }
    }
  }

  public static String expandPath(String path, Module module, Project project) {
    return new ProgramParametersConfigurator().expandPath(path, module, project);
  }

  public static String expandPathAndMacros(String path, Module module, Project project) {
    return new ProgramParametersConfigurator().expandPathAndMacros(path, module, project);
  }

  public static @Nullable Module getModule(CommonProgramRunConfigurationParameters configuration) {
    return new ProgramParametersConfigurator().getModule(configuration);
  }
}
