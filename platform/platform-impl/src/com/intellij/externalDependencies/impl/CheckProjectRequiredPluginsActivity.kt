// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalDependencies.impl

import com.intellij.externalDependencies.ExternalDependenciesManager
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class CheckProjectRequiredPluginsActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (TrustedProjects.isProjectTrusted(project)) {
      project.serviceAsync<ExternalDependenciesManager>()
    }
  }
}
