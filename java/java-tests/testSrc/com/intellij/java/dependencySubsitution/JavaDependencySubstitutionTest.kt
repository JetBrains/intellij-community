// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.dependencySubsitution

import com.intellij.java.impl.dependencySubstitution.LibraryMavenCoordinateEntity
import com.intellij.java.impl.dependencySubstitution.ModuleMavenCoordinateEntity
import com.intellij.java.impl.dependencySubstitution.mavenCoordinates
import com.intellij.java.library.MavenCoordinates
import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionUtil
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.entities.DependencyScope.COMPILE
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import org.junit.jupiter.api.Test

@TestApplication
class JavaDependencySubstitutionTest {

  @Test
  fun `test add library substitution`() {
    val storage = MutableEntityStorage.create()

    storage addEntity LibraryEntity("library", LibraryTableId.ProjectLibraryTableId, emptyList(), NonPersistentEntitySource) {
      mavenCoordinates = LibraryMavenCoordinateEntity(MavenCoordinates("org.example", "library", "1.0"), NonPersistentEntitySource)
    }
    storage addEntity ModuleEntity("app-module", emptyList(), NonPersistentEntitySource) {
      mavenCoordinates = ModuleMavenCoordinateEntity(MavenCoordinates("org.example", "app-module", "1.0"), NonPersistentEntitySource)
      dependencies += LibraryDependency(LibraryId("library", LibraryTableId.ProjectLibraryTableId), false, COMPILE)
    }
    storage addEntity ModuleEntity("lib-module", emptyList(), NonPersistentEntitySource) {
      mavenCoordinates = ModuleMavenCoordinateEntity(MavenCoordinates("org.example", "library", "1.0"), NonPersistentEntitySource)
    }

    DependencySubstitutionUtil.updateDependencySubstitutions(storage)

    ModuleAssertions.assertModuleEntity(storage, "app-module") { module ->
      DependencyAssertions.assertDependencies(module, "lib-module")
      DependencyAssertions.assertModuleDependency(module, "lib-module")
    }
  }
}
