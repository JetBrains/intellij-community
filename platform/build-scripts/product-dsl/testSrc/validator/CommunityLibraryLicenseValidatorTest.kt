// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.buildScripts.licenses.LibraryLicense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.jpsProject
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.discovery.ModuleSetSourceLabels
import org.jetbrains.intellij.build.productLayout.model.error.MissingLibraryLicenseError
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.moduleSet
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Isolated unit tests for community library license validation.
 *
 * The rule reads the module sets of the COMMUNITY and CORE labels, and reports a library that the community license
 * list does not name. An entry that only the ultimate list holds is the defect it catches, because a community product
 * reads the community list alone.
 */
@ExtendWith(TestFailureLogger::class)
class CommunityLibraryLicenseValidatorTest {
  private val unrelatedLicense = LibraryLicense(libraryName = "unrelated-lib", license = "Apache 2.0")
  private val exampleLicense = LibraryLicense(libraryName = "example-lib", license = "Apache 2.0")

  @Test
  fun `reports a library of a core module set that the community list misses`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = run(tempDir, label = ModuleSetSourceLabels.CORE, licenses = listOf(unrelatedLicense))

    assertThat(errors).hasSize(1)
    val error = errors.single() as MissingLibraryLicenseError
    assertThat(error.context).isEqualTo("the community and core module sets")
    assertThat(error.licenseFile).isEqualTo("CommunityLibraryLicenses.kt")
    val violation = error.violations.single()
    assertThat(violation.libraryName).isEqualTo("example-lib")
    assertThat(violation.moduleName).isEqualTo("intellij.libraries.example")
    assertThat(violation.coordinates).isEqualTo("com.example:example-lib:1.0")
  }

  @Test
  fun `reports a library of a community module set`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = run(tempDir, label = ModuleSetSourceLabels.COMMUNITY, licenses = listOf(unrelatedLicense))

    assertThat(errors).hasSize(1)
  }

  @Test
  fun `passes when the community list holds the entry`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = run(tempDir, label = ModuleSetSourceLabels.CORE, licenses = listOf(exampleLicense))

    assertThat(errors).isEmpty()
  }

  @Test
  fun `ignores a library of an ultimate module set`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      library("example-lib")
      library("ultimate-only-lib")
      module("intellij.libraries.example") {
        libraryDep("example-lib", exported = true)
      }
      module("intellij.libraries.ultimateOnly") {
        libraryDep("ultimate-only-lib", exported = true)
      }
    }
    // a core set keeps the scope non-empty, and its library is covered
    val coreSet = moduleSet("libraries.platform") {
      module("intellij.libraries.example")
    }
    val ultimateSet = moduleSet("ide.ultimate") {
      module("intellij.libraries.ultimateOnly")
    }
    val model = testGenerationModel(
      pluginGraph {
        moduleSet("unrelated") {
          module("intellij.libraries.example")
        }
      },
      outputProvider = jps.outputProvider,
      communityLibraryLicenses = listOf(exampleLicense),
      moduleSetsByLabel = mapOf(
        ModuleSetSourceLabels.CORE to listOf(coreSet),
        ModuleSetSourceLabels.ULTIMATE to listOf(ultimateSet),
      ),
    )
    val errors = runValidationRule(CommunityLibraryLicenseValidator, model)

    assertThat(errors).isEmpty()
  }

  @Test
  fun `fails when the scope holds no module`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    // a non-empty license list with nothing in scope would hide every misplaced entry
    assertThatThrownBy {
      runBlocking(Dispatchers.Default) { run(tempDir, label = ModuleSetSourceLabels.ULTIMATE, licenses = listOf(unrelatedLicense)) }
    }.isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("No module of a community or core module set")
  }

  @Test
  fun `an empty license list turns the rule off`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = run(tempDir, label = ModuleSetSourceLabels.CORE, licenses = emptyList())

    assertThat(errors).isEmpty()
  }

  @Test
  fun `reports a library that a member reaches through a module dependency`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      library("example-lib")
      module("intellij.libraries.example") {
        libraryDep("example-lib", exported = true)
      }
      module("intellij.platform.core") {
        moduleDep("intellij.libraries.example")
      }
    }
    val set = moduleSet("core.platform") {
      module("intellij.platform.core")
    }
    val errors = runRule(jps.outputProvider, ModuleSetSourceLabels.CORE, set, listOf(unrelatedLicense))

    assertThat(errors).hasSize(1)
    val violation = (errors.single() as MissingLibraryLicenseError).violations.single()
    assertThat(violation.libraryName).isEqualTo("example-lib")
    assertThat(violation.moduleName).isEqualTo("intellij.platform.core")
  }

  @Test
  fun `a nested set of a core set is in scope`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      library("example-lib")
      module("intellij.libraries.example") {
        libraryDep("example-lib", exported = true)
      }
    }
    val nested = moduleSet("libraries.platform") {
      module("intellij.libraries.example")
    }
    val outer = moduleSet("core.platform") {
      moduleSet(nested)
    }
    val errors = runRule(jps.outputProvider, ModuleSetSourceLabels.CORE, outer, listOf(unrelatedLicense))

    assertThat(errors).hasSize(1)
  }

  @Test
  fun `a ktor sub-library needs no entry`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      library("ktor-server-sse")
      module("intellij.libraries.ktor.server.sse") {
        libraryDep("ktor-server-sse", exported = true)
      }
    }
    val set = moduleSet("libraries.platform") {
      module("intellij.libraries.ktor.server.sse")
    }
    val errors = runRule(jps.outputProvider, ModuleSetSourceLabels.CORE, set, listOf(unrelatedLicense))

    assertThat(errors).isEmpty()
  }

  private suspend fun run(tempDir: Path, label: String, licenses: List<LibraryLicense>): List<ValidationError> {
    val jps = jpsProject(tempDir) {
      mavenLibrary("example-lib", groupId = "com.example", artifactId = "example-lib", version = "1.0")
      module("intellij.libraries.example") {
        libraryDep("example-lib", exported = true)
      }
    }
    val set = moduleSet("libraries.platform") {
      module("intellij.libraries.example")
    }
    return runRule(jps.outputProvider, label, set, licenses)
  }

  private suspend fun runRule(
    outputProvider: ModuleOutputProvider,
    label: String,
    set: ModuleSet,
    licenses: List<LibraryLicense>,
  ): List<ValidationError> {
    // the rule reads the module sets, so the graph only has to be non-empty
    val graph = pluginGraph {
      moduleSet("unrelated") {
        module("intellij.libraries.example")
      }
    }
    val model = testGenerationModel(
      graph,
      outputProvider = outputProvider,
      communityLibraryLicenses = licenses,
      moduleSetsByLabel = mapOf(label to listOf(set)),
    )
    return runValidationRule(CommunityLibraryLicenseValidator, model)
  }
}
