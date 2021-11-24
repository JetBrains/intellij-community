// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution

import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.JavaProgramPatcher
import com.intellij.execution.util.JavaParametersUtil

class ClasspathModificationPatcher: JavaProgramPatcher() {
  override fun patchJavaParameters(executor: Executor?, configuration: RunProfile?, javaParameters: JavaParameters?) {
    if (configuration is JavaRunConfigurationBase) {
      JavaParametersUtil.applyModifications(javaParameters, configuration.classpathModifications)
    }
  }
}