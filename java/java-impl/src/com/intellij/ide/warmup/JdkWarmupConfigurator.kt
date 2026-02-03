// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.warmup

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import java.nio.file.Path

public class JdkWarmupConfigurator : WarmupConfigurator {

  override val configuratorPresentableName: String = "warmupJdkConfigurator"

  override suspend fun prepareEnvironment(projectPath: Path) {
    JdkWarmupProjectActivity().configureJdk(ProjectManager.getInstance().defaultProject)
  }

  override suspend fun runWarmup(project: Project): Boolean {
    return false
  }
}
