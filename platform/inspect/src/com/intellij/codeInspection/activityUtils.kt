// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.codeInspection

import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.Observation
import org.jetbrains.annotations.ApiStatus

fun configureProjectWithActivities(project: Project, context: CommandLineInspectionProjectConfigurator.ConfiguratorContext) {
  runBlockingCancellable {
    Observation.awaitConfiguration(project, {
      context.logger.reportMessage(1, it)
    })
  }
}