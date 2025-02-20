// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.entities.DependencyScope.*
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
class ExternalProjectsWorkspaceTest : ExternalProjectsWorkspaceTestCase() {

  @Test
  fun `test add library substitution`(): Unit = timeoutRunBlocking {
    coordinates.modules["lib-module"] = "org.example:library:1.0"
    coordinates.libraries["library"] = "org.example:library:1.0"
    project.workspaceModel.update { storage ->
      storage addEntity LibraryEntity("library", LibraryTableId.ProjectLibraryTableId, emptyList(), ENTITY_SOURCE)
      storage addEntity ModuleEntity("app-module", emptyList(), ENTITY_SOURCE) {
        dependencies += LibraryDependency(LibraryId("library", LibraryTableId.ProjectLibraryTableId), false, COMPILE)
      }
      storage addEntity ModuleEntity("lib-module", emptyList(), ENTITY_SOURCE)
    }

    updateLibrarySubstitutions()

    ModuleAssertions.assertModuleEntity(project, "app-module") { module ->
      Assertions.assertEquals(ENTITY_SOURCE, module.entitySource)
      DependencyAssertions.assertDependencies(module, "lib-module")
      DependencyAssertions.assertModuleDependency(module, "lib-module") { dependency ->
        Assertions.assertEquals(false, dependency.exported)
        Assertions.assertEquals(COMPILE, dependency.scope)
      }
    }
  }

  @Test
  fun `test update library substitution`() = timeoutRunBlocking {
    coordinates.modules["lib-module"] = "org.example:library:1.0"
    coordinates.libraries["library"] = "org.example:library:1.0"
    project.workspaceModel.update { storage ->
      storage addEntity LibraryEntity("library", LibraryTableId.ProjectLibraryTableId, emptyList(), ENTITY_SOURCE)
      storage addEntity ModuleEntity("app-module", emptyList(), ENTITY_SOURCE) {
        dependencies += LibraryDependency(LibraryId("library", LibraryTableId.ProjectLibraryTableId), false, COMPILE)
      }
      storage addEntity ModuleEntity("lib-module", emptyList(), ENTITY_SOURCE)
    }

    updateLibrarySubstitutions()

    coordinates.modules["library.main"] = coordinates.modules.remove("lib-module")!!
    project.workspaceModel.update { storage ->
      storage addEntity ModuleEntity("library.main", emptyList(), ENTITY_SOURCE)
    }

    updateLibrarySubstitutions()

    ModuleAssertions.assertModuleEntity(project, "app-module") { module ->
      Assertions.assertEquals(ENTITY_SOURCE, module.entitySource)
      DependencyAssertions.assertDependencies(module, "library.main")
      DependencyAssertions.assertModuleDependency(module, "library.main") { moduleDependency ->
        Assertions.assertEquals(false, moduleDependency.exported)
        Assertions.assertEquals(COMPILE, moduleDependency.scope)
      }
    }
  }

  @Test
  fun `test remove library substitution`(): Unit = timeoutRunBlocking {
    coordinates.modules["lib-module"] = "org.example:library:1.0"
    coordinates.libraries["library"] = "org.example:library:1.0"
    project.workspaceModel.update { storage ->
      storage addEntity LibraryEntity("library", LibraryTableId.ProjectLibraryTableId, emptyList(), ENTITY_SOURCE)
      storage addEntity ModuleEntity("app-module", emptyList(), ENTITY_SOURCE) {
        dependencies += LibraryDependency(LibraryId("library", LibraryTableId.ProjectLibraryTableId), false, COMPILE)
      }
      storage addEntity ModuleEntity("lib-module", emptyList(), ENTITY_SOURCE)
    }

    updateLibrarySubstitutions()

    coordinates.modules.clear()
    coordinates.libraries.clear()

    updateLibrarySubstitutions()

    ModuleAssertions.assertModuleEntity(project, "app-module") { module ->
      Assertions.assertEquals(ENTITY_SOURCE, module.entitySource)
      DependencyAssertions.assertDependencies(module, "library")
      DependencyAssertions.assertLibraryDependency(module, "library") { dependency ->
        Assertions.assertEquals(false, dependency.exported)
        Assertions.assertEquals(COMPILE, dependency.scope)
      }
    }
  }

  @Test
  fun `test library substitution scope`(): Unit = timeoutRunBlocking {
    coordinates.modules["lib-module-compile"] = "org.example:library-compile:1.0"
    coordinates.modules["lib-module-test"] = "org.example:library-test:1.0"
    coordinates.modules["lib-module-runtime"] = "org.example:library-runtime:1.0"
    coordinates.modules["lib-module-provided"] = "org.example:library-provided:1.0"
    coordinates.libraries["library-compile"] = "org.example:library-compile:1.0"
    coordinates.libraries["library-test"] = "org.example:library-test:1.0"
    coordinates.libraries["library-runtime"] = "org.example:library-runtime:1.0"
    coordinates.libraries["library-provided"] = "org.example:library-provided:1.0"
    project.workspaceModel.update { storage ->
      storage addEntity LibraryEntity("library-compile", LibraryTableId.ProjectLibraryTableId, emptyList(), ENTITY_SOURCE)
      storage addEntity LibraryEntity("library-test", LibraryTableId.ProjectLibraryTableId, emptyList(), ENTITY_SOURCE)
      storage addEntity LibraryEntity("library-runtime", LibraryTableId.ProjectLibraryTableId, emptyList(), ENTITY_SOURCE)
      storage addEntity LibraryEntity("library-provided", LibraryTableId.ProjectLibraryTableId, emptyList(), ENTITY_SOURCE)
      storage addEntity ModuleEntity("app-module", emptyList(), ENTITY_SOURCE) {
        dependencies += LibraryDependency(LibraryId("library-compile", LibraryTableId.ProjectLibraryTableId), false, COMPILE)
        dependencies += LibraryDependency(LibraryId("library-test", LibraryTableId.ProjectLibraryTableId), false, TEST)
        dependencies += LibraryDependency(LibraryId("library-runtime", LibraryTableId.ProjectLibraryTableId), false, RUNTIME)
        dependencies += LibraryDependency(LibraryId("library-provided", LibraryTableId.ProjectLibraryTableId), false, PROVIDED)
      }
      storage addEntity ModuleEntity("lib-module-compile", emptyList(), ENTITY_SOURCE)
      storage addEntity ModuleEntity("lib-module-test", emptyList(), ENTITY_SOURCE)
      storage addEntity ModuleEntity("lib-module-runtime", emptyList(), ENTITY_SOURCE)
      storage addEntity ModuleEntity("lib-module-provided", emptyList(), ENTITY_SOURCE)
    }

    updateLibrarySubstitutions()

    ModuleAssertions.assertModuleEntity(project, "app-module") { module ->
      Assertions.assertEquals(ENTITY_SOURCE, module.entitySource)
      DependencyAssertions.assertDependencies(module, "lib-module-compile", "lib-module-test", "lib-module-runtime", "lib-module-provided")
      DependencyAssertions.assertModuleDependency(module, "lib-module-compile") { dependency ->
        Assertions.assertEquals(COMPILE, dependency.scope)
      }
      DependencyAssertions.assertModuleDependency(module, "lib-module-test") { dependency ->
        Assertions.assertEquals(TEST, dependency.scope)
      }
      DependencyAssertions.assertModuleDependency(module, "lib-module-runtime") { dependency ->
        Assertions.assertEquals(RUNTIME, dependency.scope)
      }
      DependencyAssertions.assertModuleDependency(module, "lib-module-provided") { dependency ->
        Assertions.assertEquals(PROVIDED, dependency.scope)
      }
    }

    coordinates.modules.clear()
    coordinates.libraries.clear()

    updateLibrarySubstitutions()

    ModuleAssertions.assertModuleEntity(project, "app-module") { module ->
      Assertions.assertEquals(ENTITY_SOURCE, module.entitySource)
      DependencyAssertions.assertDependencies(module, "library-compile", "library-test", "library-runtime", "library-provided")
      DependencyAssertions.assertLibraryDependency(module, "library-compile") { dependency ->
        Assertions.assertEquals(COMPILE, dependency.scope)
      }
      DependencyAssertions.assertLibraryDependency(module, "library-test") { dependency ->
        Assertions.assertEquals(TEST, dependency.scope)
      }
      DependencyAssertions.assertLibraryDependency(module, "library-runtime") { dependency ->
        Assertions.assertEquals(RUNTIME, dependency.scope)
      }
      DependencyAssertions.assertLibraryDependency(module, "library-provided") { dependency ->
        Assertions.assertEquals(PROVIDED, dependency.scope)
      }
    }
  }

  @Test
  fun `test library substitution exported`(): Unit = timeoutRunBlocking {
    coordinates.modules["lib-module"] = "org.example:library:1.0"
    coordinates.modules["lib-module-exported"] = "org.example:library-exported:1.0"
    coordinates.libraries["library"] = "org.example:library:1.0"
    coordinates.libraries["library-exported"] = "org.example:library-exported:1.0"
    project.workspaceModel.update { storage ->
      storage addEntity LibraryEntity("library", LibraryTableId.ProjectLibraryTableId, emptyList(), ENTITY_SOURCE)
      storage addEntity LibraryEntity("library-exported", LibraryTableId.ProjectLibraryTableId, emptyList(), ENTITY_SOURCE)
      storage addEntity ModuleEntity("app-module", emptyList(), ENTITY_SOURCE) {
        dependencies += LibraryDependency(LibraryId("library", LibraryTableId.ProjectLibraryTableId), false, COMPILE)
        dependencies += LibraryDependency(LibraryId("library-exported", LibraryTableId.ProjectLibraryTableId), true, COMPILE)
      }
      storage addEntity ModuleEntity("lib-module", emptyList(), ENTITY_SOURCE)
      storage addEntity ModuleEntity("lib-module-exported", emptyList(), ENTITY_SOURCE)
    }

    updateLibrarySubstitutions()

    ModuleAssertions.assertModuleEntity(project, "app-module") { module ->
      Assertions.assertEquals(ENTITY_SOURCE, module.entitySource)
      DependencyAssertions.assertDependencies(module, "lib-module", "lib-module-exported")
      DependencyAssertions.assertModuleDependency(module, "lib-module") { dependency ->
        Assertions.assertEquals(false, dependency.exported)
      }
      DependencyAssertions.assertModuleDependency(module, "lib-module-exported") { dependency ->
        Assertions.assertEquals(true, dependency.exported)
      }
    }

    coordinates.modules.clear()
    coordinates.libraries.clear()

    updateLibrarySubstitutions()

    ModuleAssertions.assertModuleEntity(project, "app-module") { module ->
      Assertions.assertEquals(ENTITY_SOURCE, module.entitySource)
      DependencyAssertions.assertDependencies(module, "library", "library-exported")
      DependencyAssertions.assertLibraryDependency(module, "library") { dependency ->
        Assertions.assertEquals(false, dependency.exported)
      }
      DependencyAssertions.assertLibraryDependency(module, "library-exported") { dependency ->
        Assertions.assertEquals(true, dependency.exported)
      }
    }
  }
}