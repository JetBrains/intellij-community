// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.impl.frontend

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity


private class FrontendRunInitializationProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    // initialize the run manager to start listening for backend state
    FrontendRunContentService.getInstance(project)
  }
}