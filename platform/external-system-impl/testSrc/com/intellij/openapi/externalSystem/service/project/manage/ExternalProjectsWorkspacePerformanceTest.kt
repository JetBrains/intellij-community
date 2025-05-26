// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.openapi.externalSystem.service.project.ExternalProjectsWorkspaceTestCase
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.entities.DependencyScope.COMPILE
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@TestApplication
class ExternalProjectsWorkspacePerformanceTest : ExternalProjectsWorkspaceTestCase() {

  companion object {
    private const val TEST_SOURCE = """
      1,    1,   1,   1
      100,  100, 100, 100
      1000, 100, 100, 10"""
  }

  @ParameterizedTest
  @CsvSource(textBlock = TEST_SOURCE)
  fun `test correctness of dependency substitution performance test`(
    numAppModules: Int,
    numModules: Int,
    numLibraries: Int,
    numSubstitutions: Int,
  ): Unit = runBlocking {

    val testData = TestData(numAppModules, numModules, numLibraries, numSubstitutions)

    cleanUp()
    assertStateAfterCleanUp()

    setUp(testData)
    assertStateAfterSetUp(testData)

    updateLibrarySubstitutions()
    assertStateAfterLibrarySubstitution(testData)

    cleanUp()
    assertStateAfterCleanUp()
  }

  @ParameterizedTest
  @CsvSource(textBlock = TEST_SOURCE)
  fun `test dependency substitution performance`(
    numAppModules: Int,
    numModules: Int,
    numLibraries: Int,
    numSubstitutions: Int,
  ) {

    val testData = TestData(numAppModules, numModules, numLibraries, numSubstitutions)

    Benchmark.newBenchmark("($numAppModules, $numModules, $numLibraries, $numSubstitutions)") {
      runBlocking {
        updateLibrarySubstitutions()
      }
    }.setup {
      runBlocking {
        cleanUp()
        setUp(testData)
      }
    }.start()
  }

  private class TestData(
    numAppModules: Int,
    numModules: Int,
    numLibraries: Int,
    numSubstitutions: Int,
  ) {
    val appModules = (0 until numAppModules).associate { "app-module-$it" to "org.example:app-module-$it:1.0" }
    val modules = (0 until numModules).associate { "module-$it" to "org.example:module-$it:1.0" }
    val libraries = (0 until numLibraries).associate { "library-$it" to "org.example:library-$it:1.0" }
    val substitutions = (0 until numSubstitutions).associate { ("module-subst-$it" to "library-subst-$it") to "org.example:subst-$it:1.0" }
    val moduleSubstitutions = substitutions.mapKeys { it.key.first }
    val librarySubstitutions = substitutions.mapKeys { it.key.second }
  }

  private suspend fun cleanUp() {

    coordinates.modules.clear()
    coordinates.libraries.clear()

    project.workspaceModel.update { storage ->
      storage.replaceBySource({ it == ENTITY_SOURCE }, ImmutableEntityStorage.empty())
    }
  }

  private fun assertStateAfterCleanUp() {

    Assertions.assertTrue(coordinates.modules.isEmpty()) {
      "Module coordinates should be empty, after test cleanup"
    }
    Assertions.assertTrue(coordinates.libraries.isEmpty()) {
      "Library coordinates should be empty, after test cleanup"
    }

    ModuleAssertions.assertModules(project, emptyList())
  }

  private suspend fun setUp(testData: TestData) {

    coordinates.modules.putAll(testData.appModules)
    coordinates.modules.putAll(testData.modules)
    coordinates.modules.putAll(testData.moduleSubstitutions)
    coordinates.libraries.putAll(testData.libraries)
    coordinates.libraries.putAll(testData.librarySubstitutions)

    project.workspaceModel.update { storage ->
      for (moduleName in testData.modules.keys + testData.moduleSubstitutions.keys) {
        storage addEntity ModuleEntity(moduleName, emptyList(), ENTITY_SOURCE)
      }
      for (libraryName in testData.libraries.keys + testData.librarySubstitutions.keys) {
        storage addEntity LibraryEntity(libraryName, LibraryTableId.ProjectLibraryTableId, emptyList(), ENTITY_SOURCE)
      }
      for (appModuleName in testData.appModules.keys) {
        storage addEntity ModuleEntity(appModuleName, emptyList(), ENTITY_SOURCE) {
          dependencies += testData.modules.keys.map { moduleName ->
            ModuleDependency(ModuleId(moduleName), false, COMPILE, false)
          }
          dependencies += (testData.libraries.keys + testData.librarySubstitutions.keys).map { libraryName ->
            LibraryDependency(LibraryId(libraryName, LibraryTableId.ProjectLibraryTableId), false, COMPILE)
          }
        }
      }
    }
  }

  private fun assertStateAfterSetUp(testData: TestData) {

    Assertions.assertTrue(coordinates.modules.isNotEmpty()) {
      "Module coordinates should be prepared, after test setup"
    }
    Assertions.assertTrue(coordinates.libraries.isNotEmpty()) {
      "Library coordinates should be prepared, after test setup"
    }

    ModuleAssertions.assertModules(
      project, (testData.appModules.keys + testData.modules.keys + testData.moduleSubstitutions.keys).toList()
    )
    for (appModuleName in testData.appModules.keys) {
      ModuleAssertions.assertModuleEntity(project, appModuleName) { module ->
        Assertions.assertEquals(ENTITY_SOURCE, module.entitySource)
        DependencyAssertions.assertDependencies(
          module, (testData.modules.keys + testData.libraries.keys + testData.librarySubstitutions.keys).toList()
        )
        for (moduleName in testData.modules.keys) {
          DependencyAssertions.assertModuleDependency(module, moduleName) { dependency ->
            Assertions.assertFalse(dependency.exported) {
              "The $moduleName module dependency shouldn't be exported"
            }
            Assertions.assertEquals(COMPILE, dependency.scope) {
              "The $moduleName module dependency should be compile"
            }
          }
        }
        for (libraryName in testData.libraries.keys + testData.librarySubstitutions.keys) {
          DependencyAssertions.assertLibraryDependency(module, libraryName) { dependency ->
            Assertions.assertFalse(dependency.exported) {
              "The $libraryName library dependency shouldn't be exported"
            }
            Assertions.assertEquals(COMPILE, dependency.scope) {
              "The $libraryName library dependency should be compile"
            }
          }
        }
      }
    }
  }

  private fun assertStateAfterLibrarySubstitution(testData: TestData) {

    Assertions.assertTrue(coordinates.modules.isNotEmpty()) {
      "Module coordinates should be keep, after test"
    }
    Assertions.assertTrue(coordinates.libraries.isNotEmpty()) {
      "Library coordinates should be keep, after test"
    }

    ModuleAssertions.assertModules(
      project, (testData.appModules.keys + testData.modules.keys + testData.moduleSubstitutions.keys).toList()
    )
    for (appModuleName in testData.appModules.keys) {
      ModuleAssertions.assertModuleEntity(project, appModuleName) { module ->
        Assertions.assertEquals(ENTITY_SOURCE, module.entitySource)
        DependencyAssertions.assertDependencies(
          module, (testData.modules.keys + testData.moduleSubstitutions.keys + testData.libraries.keys).toList()
        )
        for (moduleName in testData.modules.keys + testData.moduleSubstitutions.keys) {
          DependencyAssertions.assertModuleDependency(module, moduleName) { dependency ->
            Assertions.assertFalse(dependency.exported) {
              "The $moduleName module dependency shouldn't be exported"
            }
            Assertions.assertEquals(COMPILE, dependency.scope) {
              "The $moduleName module dependency should be compile"
            }
          }
        }
        for (libraryName in testData.libraries.keys) {
          DependencyAssertions.assertLibraryDependency(module, libraryName) { dependency ->
            Assertions.assertFalse(dependency.exported) {
              "The $libraryName library dependency shouldn't be exported"
            }
            Assertions.assertEquals(COMPILE, dependency.scope) {
              "The $libraryName library dependency should be compile"
            }
          }
        }
      }
    }
  }
}