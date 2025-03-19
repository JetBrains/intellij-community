// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.CommandLineInspectionProjectConfigurator.ConfiguratorContext
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface CommandLineInspectionProjectAsyncConfigurator : CommandLineInspectionProjectConfigurator {
  suspend fun configureProjectAsync(project: Project, context: ConfiguratorContext)

  override fun configureProject(project: Project, context: ConfiguratorContext) {
    runBlockingCancellable { configureProjectAsync(project, context) }
  }
}