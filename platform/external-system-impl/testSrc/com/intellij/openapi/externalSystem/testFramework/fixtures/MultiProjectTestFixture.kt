// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.testFramework.fixtures

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project

interface MultiProjectTestFixture {

  suspend fun openProject(relativePath: String): Project

  suspend fun linkProject(project: Project, relativePath: String, systemId: ProjectSystemId)

  suspend fun unlinkProject(project: Project, relativePath: String, systemId: ProjectSystemId)

  suspend fun awaitOpenProjectConfiguration(openProject: suspend () -> Project): Project

  suspend fun <R> awaitProjectConfiguration(project: Project, action: suspend () -> R): R
}