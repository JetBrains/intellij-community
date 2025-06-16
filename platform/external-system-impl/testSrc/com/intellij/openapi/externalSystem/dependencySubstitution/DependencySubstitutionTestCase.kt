// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependencySubstitution

import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionExtension
import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionUtil.intersect
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.testFramework.junit5.fixture.extensionPointFixture

abstract class DependencySubstitutionTestCase {

  val coordinates by extensionPointFixture(DependencySubstitutionExtension.EP_NAME, ::TestDependencySubstitutionExtension)

  class TestDependencySubstitutionExtension : DependencySubstitutionExtension {

    val modules = HashMap<String, String>()
    val libraries = HashMap<String, String>()

    override fun buildLibraryToModuleMap(storage: EntityStorage): Map<LibraryId, ModuleId> {
      val libraries = storage.entities<LibraryEntity>()
        .associate { libraries[it.name] to it.symbolicId }
      val modules = storage.entities<ModuleEntity>()
        .associate { modules[it.name] to it.symbolicId }
      return libraries.intersect(modules)
    }
  }
}