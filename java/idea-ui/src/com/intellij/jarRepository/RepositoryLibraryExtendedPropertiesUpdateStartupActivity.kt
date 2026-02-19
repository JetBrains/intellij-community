// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.application

class RepositoryLibraryExtendedPropertiesUpdateStartupActivity : ProjectActivity {
  init {
    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val service = project.serviceAsync<RepositoryLibraryUtils>()
    service.subscribeToWorkspaceModelUpdates()
  }
}