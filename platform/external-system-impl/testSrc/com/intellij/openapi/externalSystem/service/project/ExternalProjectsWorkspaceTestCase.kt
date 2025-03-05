// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.Library
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.projectFixture

@TestApplication
abstract class ExternalProjectsWorkspaceTestCase {

  val project by projectFixture()
  val coordinates by extensionPointFixture(ExternalSystemCoordinateContributor.EP_NAME, ::TestCoordinateContributor)

  suspend fun updateLibrarySubstitutions() {
    writeAction {
      IdeModifiableModelsProviderImpl(project)
        .commit()
    }
  }

  suspend fun WorkspaceModel.update(updater: (MutableEntityStorage) -> Unit) =
    update("Test description", updater)

  class TestCoordinateContributor : ExternalSystemCoordinateContributor {

    val modules = HashMap<String, String>()
    val libraries = HashMap<String, String>()

    override fun findModuleCoordinate(module: Module): ProjectCoordinate? =
      modules[module.name]?.toProjectCoordinate()

    override fun findLibraryCoordinate(library: Library): ProjectCoordinate? =
      libraries[library.name]?.toProjectCoordinate()

    private fun String.toProjectCoordinate(): ProjectCoordinate {
      val (groupId, artifactId, version) = split(":")
      return ProjectId(groupId, artifactId, version)
    }
  }

  companion object {

    val ENTITY_SOURCE = object : EntitySource {}
  }
}