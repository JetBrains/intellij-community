// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.buildScripts.licenses.LibraryLicense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.jpsProject
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.MissingLibraryLicenseError
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Isolated unit tests for library license validation.
 *
 * The rule reads the production runtime libraries of every production module that the plugin graph holds. It reports a
 * library that no license entry names. The tests use the [jpsProject] DSL for the JPS modules, the [pluginGraph] DSL for
 * the scope, and [runValidationRule] for the pipeline interface.
 */
@ExtendWith(TestFailureLogger::class)
class LibraryLicenseValidatorTest {
  private val unrelatedLicense = LibraryLicense(libraryName = "unrelated-lib", license = "Apache 2.0")

  @Test
  fun `reports an uncovered library inside a library wrapper module`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      mavenLibrary("example-lib", groupId = "com.example", artifactId = "example-lib", version = "1.0")
      module("intellij.libraries.example") {
        libraryDep("example-lib", exported = true)
      }
    }

    val graph = pluginGraph {
      moduleSet("libraries") {
        module("intellij.libraries.example")
      }
    }
    val model = testGenerationModel(graph, outputProvider = jps.outputProvider, libraryLicenses = listOf(unrelatedLicense))
    val errors = runValidationRule(LibraryLicenseValidator, model)

    assertThat(errors).hasSize(1)
    val error = errors.single() as MissingLibraryLicenseError
    assertThat(error.context).isEqualTo("the plugin graph")
    val violation = error.violations.single()
    assertThat(violation.libraryName).isEqualTo("example-lib")
    assertThat(violation.moduleName).isEqualTo("intellij.libraries.example")
    assertThat(violation.coordinates).isEqualTo("com.example:example-lib:1.0")
  }

  @Test
  fun `reports a library of the main module of a plugin`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      library("example-lib")
      module("intellij.terraform") {
        libraryDep("example-lib")
      }
    }

    // the graph holds the main module of a plugin as a plugin, and not as a content module
    val graph = pluginGraph {
      plugin("intellij.terraform")
    }
    val model = testGenerationModel(graph, outputProvider = jps.outputProvider, libraryLicenses = listOf(unrelatedLicense))
    val errors = runValidationRule(LibraryLicenseValidator, model)

    assertThat(errors).hasSize(1)
    val violation = (errors.single() as MissingLibraryLicenseError).violations.single()
    assertThat(violation.libraryName).isEqualTo("example-lib")
    assertThat(violation.moduleName).isEqualTo("intellij.terraform")
  }

  @Test
  fun `reports a library that a content module reaches through a module dependency`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      library("example-lib")
      module("intellij.libraries.example") {
        libraryDep("example-lib", exported = true)
      }
      module("intellij.platform.core") {
        moduleDep("intellij.libraries.example")
      }
    }

    // the enumerator is transitive, so the graph does not need the library wrapper module
    val graph = pluginGraph {
      moduleSet("platform") {
        module("intellij.platform.core")
      }
    }
    val model = testGenerationModel(graph, outputProvider = jps.outputProvider, libraryLicenses = listOf(unrelatedLicense))
    val errors = runValidationRule(LibraryLicenseValidator, model)

    assertThat(errors).hasSize(1)
    assertThat((errors.single() as MissingLibraryLicenseError).violations.single().libraryName).isEqualTo("example-lib")
  }

  @Test
  fun `accepts a library that an additional library name covers`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      library("example-lib")
      module("intellij.libraries.example") {
        libraryDep("example-lib", exported = true)
      }
    }

    // the real fix used the additional name form, so the test uses it too
    val license = LibraryLicense(libraryName = "example", license = "Apache 2.0").additionalLibraryNames("example-lib")
    val graph = pluginGraph {
      moduleSet("libraries") {
        module("intellij.libraries.example")
      }
    }
    val model = testGenerationModel(graph, outputProvider = jps.outputProvider, libraryLicenses = listOf(license))

    assertThat(runValidationRule(LibraryLicenseValidator, model)).isEmpty()
  }

  @Test
  fun `ignores a module that the graph does not hold`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      library("example-lib")
      module("intellij.platform.guiTests") {
        libraryDep("example-lib")
      }
      module("intellij.platform.core")
    }

    val graph = pluginGraph {
      moduleSet("platform") {
        module("intellij.platform.core")
      }
    }
    val model = testGenerationModel(graph, outputProvider = jps.outputProvider, libraryLicenses = listOf(unrelatedLicense))

    assertThat(runValidationRule(LibraryLicenseValidator, model)).isEmpty()
  }

  @Test
  fun `ignores a module that only a test plugin declares as content`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      library("example-lib")
      module("intellij.platform.testFramework") {
        libraryDep("example-lib")
      }
      module("intellij.platform.core")
    }

    val graph = pluginGraph {
      moduleSet("platform") {
        module("intellij.platform.core")
      }
      testPlugin("intellij.platform.testFramework.plugin") {
        content("intellij.platform.testFramework")
      }
    }
    val model = testGenerationModel(graph, outputProvider = jps.outputProvider, libraryLicenses = listOf(unrelatedLicense))

    assertThat(runValidationRule(LibraryLicenseValidator, model)).isEmpty()
  }

  @Test
  fun `skips an implicit ktor sub-library`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      library("ktor-io")
      module("intellij.libraries.ktor.io") {
        libraryDep("ktor-io", exported = true)
      }
    }

    val graph = pluginGraph {
      moduleSet("libraries") {
        module("intellij.libraries.ktor.io")
      }
    }
    val model = testGenerationModel(graph, outputProvider = jps.outputProvider, libraryLicenses = listOf(unrelatedLicense))

    assertThat(runValidationRule(LibraryLicenseValidator, model)).isEmpty()
  }

  @Test
  fun `reports ktor-client because it is a main library`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      library("ktor-client")
      module("intellij.libraries.ktor.client") {
        libraryDep("ktor-client", exported = true)
      }
    }

    val graph = pluginGraph {
      moduleSet("libraries") {
        module("intellij.libraries.ktor.client")
      }
    }
    val model = testGenerationModel(graph, outputProvider = jps.outputProvider, libraryLicenses = listOf(unrelatedLicense))
    val errors = runValidationRule(LibraryLicenseValidator, model)

    assertThat(errors).hasSize(1)
    assertThat((errors.single() as MissingLibraryLicenseError).violations.single().libraryName).isEqualTo("ktor-client")
  }

  @Test
  fun `an empty license list turns the rule off`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      library("example-lib")
      module("intellij.libraries.example") {
        libraryDep("example-lib", exported = true)
      }
    }

    val graph = pluginGraph {
      moduleSet("libraries") {
        module("intellij.libraries.example")
      }
    }
    val model = testGenerationModel(graph, outputProvider = jps.outputProvider)

    assertThat(runValidationRule(LibraryLicenseValidator, model)).isEmpty()
  }

  @Test
  fun `raises when the graph holds no production module and the license list is not empty`() {
    val model = testGenerationModel(pluginGraph {}, libraryLicenses = listOf(unrelatedLicense))

    assertThatThrownBy {
      runBlocking(Dispatchers.Default) {
        runValidationRule(LibraryLicenseValidator, model)
      }
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("The plugin graph has no production module")
      .hasMessageContaining("A pass here would hide every missing license entry")
  }
}
