// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependencySubstitution

import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionUtil
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@StressTestApplication
@TestApplication
class DependencySubstitutionPerformanceTest : DependencySubstitutionTestCase() {

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
  ) {
    val storage = MutableEntityStorage.create()
    val testData = TestData(numAppModules, numModules, numLibraries, numSubstitutions)

    cleanUp(storage)
    assertStateAfterCleanUp(storage)

    setUp(storage, testData)
    assertStateAfterSetUp(storage, testData)

    DependencySubstitutionUtil.updateDependencySubstitutions(storage)
    assertStateAfterLibrarySubstitution(storage, testData)

    cleanUp(storage)
    assertStateAfterCleanUp(storage)
  }

  @ParameterizedTest
  @CsvSource(textBlock = TEST_SOURCE)
  fun `test dependency substitution performance`(
    numAppModules: Int,
    numModules: Int,
    numLibraries: Int,
    numSubstitutions: Int,
  ) {
    val storage = MutableEntityStorage.create()
    val testData = TestData(numAppModules, numModules, numLibraries, numSubstitutions)

    Benchmark.newBenchmark("($numAppModules, $numModules, $numLibraries, $numSubstitutions)") {
      DependencySubstitutionUtil.updateDependencySubstitutions(storage)
    }.setup {
      cleanUp(storage)
      setUp(storage, testData)
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

  private fun cleanUp(storage: MutableEntityStorage) {
    coordinates.modules.clear()
    coordinates.libraries.clear()
    storage.replaceBySource({ true }, ImmutableEntityStorage.empty())
  }

  private fun assertStateAfterCleanUp(storage: EntityStorage) {
    Assertions.assertTrue(coordinates.modules.isEmpty()) {
      "Module coordinates should be empty, after test cleanup"
    }
    Assertions.assertTrue(coordinates.libraries.isEmpty()) {
      "Library coordinates should be empty, after test cleanup"
    }
    ModuleAssertions.assertModules(storage, emptyList())
  }

  private fun setUp(storage: MutableEntityStorage, testData: TestData) {

    coordinates.modules.putAll(testData.appModules)
    coordinates.modules.putAll(testData.modules)
    coordinates.modules.putAll(testData.moduleSubstitutions)
    coordinates.libraries.putAll(testData.libraries)
    coordinates.libraries.putAll(testData.librarySubstitutions)

    for (moduleName in testData.modules.keys + testData.moduleSubstitutions.keys) {
      storage addEntity ModuleEntity.Companion(moduleName, emptyList(), NonPersistentEntitySource)
    }
    for (libraryName in testData.libraries.keys + testData.librarySubstitutions.keys) {
      storage addEntity LibraryEntity.Companion(libraryName, LibraryTableId.ProjectLibraryTableId, emptyList(), NonPersistentEntitySource)
    }
    for (appModuleName in testData.appModules.keys) {
      storage addEntity ModuleEntity.Companion(appModuleName, emptyList(), NonPersistentEntitySource) {
        dependencies += testData.modules.keys.map { moduleName ->
          ModuleDependency(ModuleId(moduleName), false, DependencyScope.COMPILE, false)
        }
        dependencies += (testData.libraries.keys + testData.librarySubstitutions.keys).map { libraryName ->
          LibraryDependency(LibraryId(libraryName, LibraryTableId.ProjectLibraryTableId), false, DependencyScope.COMPILE)
        }
      }
    }
  }

  private fun assertStateAfterSetUp(storage: EntityStorage, testData: TestData) {
    Assertions.assertTrue(coordinates.modules.isNotEmpty()) {
      "Module coordinates should be prepared, after test setup"
    }
    Assertions.assertTrue(coordinates.libraries.isNotEmpty()) {
      "Library coordinates should be prepared, after test setup"
    }
    ModuleAssertions.assertModules(
      storage, (testData.appModules.keys + testData.modules.keys + testData.moduleSubstitutions.keys).toList()
    )
    for (appModuleName in testData.appModules.keys) {
      ModuleAssertions.assertModuleEntity(storage, appModuleName) { module ->
        DependencyAssertions.assertDependencies(
          module, (testData.modules.keys + testData.libraries.keys + testData.librarySubstitutions.keys).toList()
        )
        for (moduleName in testData.modules.keys) {
          DependencyAssertions.assertModuleDependency(module, moduleName) { dependency ->
            Assertions.assertFalse(dependency.exported) {
              "The $moduleName module dependency shouldn't be exported"
            }
            Assertions.assertEquals(DependencyScope.COMPILE, dependency.scope) {
              "The $moduleName module dependency should be compile"
            }
          }
        }
        for (libraryName in testData.libraries.keys + testData.librarySubstitutions.keys) {
          DependencyAssertions.assertLibraryDependency(module, libraryName) { dependency ->
            Assertions.assertFalse(dependency.exported) {
              "The $libraryName library dependency shouldn't be exported"
            }
            Assertions.assertEquals(DependencyScope.COMPILE, dependency.scope) {
              "The $libraryName library dependency should be compile"
            }
          }
        }
      }
    }
  }

  private fun assertStateAfterLibrarySubstitution(storage: EntityStorage, testData: TestData) {
    Assertions.assertTrue(coordinates.modules.isNotEmpty()) {
      "Module coordinates should be keep, after test"
    }
    Assertions.assertTrue(coordinates.libraries.isNotEmpty()) {
      "Library coordinates should be keep, after test"
    }
    ModuleAssertions.assertModules(
      storage, (testData.appModules.keys + testData.modules.keys + testData.moduleSubstitutions.keys).toList()
    )
    for (appModuleName in testData.appModules.keys) {
      ModuleAssertions.assertModuleEntity(storage, appModuleName) { module ->
        DependencyAssertions.assertDependencies(
          module, (testData.modules.keys + testData.moduleSubstitutions.keys + testData.libraries.keys).toList()
        )
        for (moduleName in testData.modules.keys + testData.moduleSubstitutions.keys) {
          DependencyAssertions.assertModuleDependency(module, moduleName) { dependency ->
            Assertions.assertFalse(dependency.exported) {
              "The $moduleName module dependency shouldn't be exported"
            }
            Assertions.assertEquals(DependencyScope.COMPILE, dependency.scope) {
              "The $moduleName module dependency should be compile"
            }
          }
        }
        for (libraryName in testData.libraries.keys) {
          DependencyAssertions.assertLibraryDependency(module, libraryName) { dependency ->
            Assertions.assertFalse(dependency.exported) {
              "The $libraryName library dependency shouldn't be exported"
            }
            Assertions.assertEquals(DependencyScope.COMPILE, dependency.scope) {
              "The $libraryName library dependency should be compile"
            }
          }
        }
      }
    }
  }
}