// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EnvironmentVariablesConfiguration")
package com.intellij.execution.configuration

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.EnvFilesOptions

fun EnvironmentVariablesComponent.resetFromConfiguration(parameters: CommonProgramRunConfigurationParameters) {
  envs = parameters.getEnvs()
  isPassParentEnvs = parameters.isPassParentEnvs()
  if (parameters is EnvFilesOptions) {
    myEnvVars.setEnvFilePaths(parameters.envFilePaths)
  }
  if (parameters is EnvironmentFileOptions) {
    myEnvVars.setData(myEnvVars.data.with(parameters.environmentFile))
  }
}

fun EnvironmentVariablesComponent.applyToConfiguration(parameters: CommonProgramRunConfigurationParameters) {
  parameters.setEnvs(envs)
  parameters.setPassParentEnvs(isPassParentEnvs)
  if (parameters is EnvFilesOptions) {
    parameters.envFilePaths = myEnvVars.getEnvFilePaths()
  }
  if (parameters is EnvironmentFileOptions) {
    parameters.setEnvironmentFile(envData.environmentFile);
  }
}