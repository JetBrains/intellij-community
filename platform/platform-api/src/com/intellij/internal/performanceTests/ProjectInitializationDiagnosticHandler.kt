// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.performanceTests

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.util.function.Supplier

interface ProjectInitializationDiagnosticHandler {
  companion object {
    val EP_NAME: ExtensionPointName<ProjectInitializationDiagnosticHandler> =
      ExtensionPointName("com.intellij.internal.performanceTests.projectInitializationDiagnosticHandler")
  }

  fun registerBeginningOfInitializationActivity(project: Project, debugMessageProducer: Supplier<String>): ProjectInitializationDiagnostic.ActivityTracker

  fun isProjectInitializationAndIndexingFinished(project: Project): Boolean
}
