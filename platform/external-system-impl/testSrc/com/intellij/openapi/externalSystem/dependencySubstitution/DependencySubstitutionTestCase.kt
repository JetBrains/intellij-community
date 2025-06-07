// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependencySubstitution

import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionCoordinateContributor
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.testFramework.junit5.fixture.extensionPointFixture

abstract class DependencySubstitutionTestCase {

  val coordinates by extensionPointFixture(DependencySubstitutionCoordinateContributor.EP_NAME, ::TestCoordinateContributor)

  class TestCoordinateContributor : DependencySubstitutionCoordinateContributor {

    val modules = HashMap<String, String>()
    val libraries = HashMap<String, String>()

    override fun findModuleCoordinate(module: ModuleEntity): String? =
      modules[module.name]

    override fun findLibraryCoordinate(library: LibraryEntity): String? =
      libraries[library.name]
  }
}