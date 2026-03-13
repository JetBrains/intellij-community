// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.assertj.core.api.JUnitSoftAssertions
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText

/**
 * run tests via `bazel test //:bazel-generator-integration-tests --test_output=all`
 * from `community/platform/build-scripts/bazel`
 */
@OptIn(ExperimentalPathApi::class)
class BazelGeneratorIntegrationTests {
  companion object {
    private const val TEST_DATA_MARKER_ENV = "BAZEL_GENERATOR_INTEGRATION_TEST_DATA_MARKER"

    // Expected BUILD.bazel files are stored as ~BUILD.bazel in test data
    // to prevent Bazel from treating those directories as packages (same convention as plugins/bazel)
    private const val TILDE_BUILD_PREFIX = "~"
  }

  @JvmField
  @Rule
  val softly = JUnitSoftAssertions()

  @Test fun kotlinSnapshotLibrary() = doTest("kotlin-snapshot-library")
  @Test fun snapshotRepositoryLibrary() = doTest("snapshot-repository-library")
  @Test fun snapshotLibrary() = doTest("snapshot-library")
  @Test fun snapshotLibraryInTree() = doTest("snapshot-library-in-tree")
  @Test fun moduleRepositoryLibrarySnapshot() = doTest("module-repository-library-snapshot")

  private fun doTest(
    testName: String,
    runWithoutUltimateRoot: Boolean = true,
    defaultCustomModules: Boolean = false,
  ) {
    val testDataPath = getTestDataPath(testName)

    val projectDataPath = testDataPath.resolve("project")
    assertTrue("$projectDataPath is not a directory", projectDataPath.isDirectory())
    for (path in projectDataPath.walk()) {
      check(!path.name.startsWith(TILDE_BUILD_PREFIX)) {
        "Initial project dir is not expected to contain $TILDE_BUILD_PREFIX files, but $path is"
      }
    }

    val expectedDataPath = testDataPath.resolve("expected")
    assertTrue("$expectedDataPath is not a directory", expectedDataPath.isDirectory())

    // can be missing
    val m2RepoPath = testDataPath.resolve("m2-repo")

    val tempDir = Files.createTempDirectory("test-$testName")
    projectDataPath.copyToRecursively(tempDir, followLinks = true, overwrite = false)

    JpsModuleToBazel.main(
      arrayOf(
        "--workspace_directory=$tempDir",
        "--run_without_ultimate_root=$runWithoutUltimateRoot",
        "--default-custom-modules=$defaultCustomModules",
        "--m2-repo=$m2RepoPath",
      )
    )

    for (path in tempDir.walk()) {
      // mangle names in output dir so file names will be the same as in expectedDataPath (with ~)
      if (path.name == "BUILD.bazel") {
        path.moveTo(path.resolveSibling(TILDE_BUILD_PREFIX + path.name))
      }
    }

    assertAndRemoveSameFiles(projectDataPath, tempDir)
    compareDirectories(expectedDataPath, tempDir)

    if (!softly.wasSuccess()) {
      // do not delete tempDir on tests failure, it is used in IDE to update expected file
      tempDir.deleteRecursively()
    }
  }

  private fun getTestDataPath(testName: String): Path {
    return Path.of(requireEnv("TEST_SRCDIR"), requireEnv(TEST_DATA_MARKER_ENV)).parent.resolve(testName).normalize()
  }

  private fun requireEnv(name: String): String {
    val value = System.getenv(name)
    assertTrue("Missing $name env variable in bazel test environment", !value.isNullOrBlank())
    return value.orEmpty()
  }

  private fun assertAndRemoveSameFiles(initialProjectDir: Path, testOutputDir: Path) {
    for (initialProjectChildPath in initialProjectDir.listDirectoryEntries()) {
      val outputChildPath = testOutputDir.resolve(initialProjectChildPath.name)
      if (initialProjectChildPath.isDirectory()) {
        check(outputChildPath.isDirectory()) {
           "$outputChildPath is not a directory, but $initialProjectChildPath is"
        }

        assertAndRemoveSameFiles(initialProjectChildPath, outputChildPath)
      }
      else if (outputChildPath.readText() == initialProjectChildPath.readText()) {
        outputChildPath.deleteExisting()
      }
      else {
        softly.assertThatCharSequence(outputChildPath.readText())
          .withFailMessage {
            "Actual file $outputChildPath is different from initial project file at $initialProjectChildPath. " +
            "Generator should not modify existing project model files"
          }
          .isEqualTo(initialProjectChildPath.readText())
      }
    }
  }

  private fun compareDirectories(expected: Path, actual: Path) {
    val expectedChildren = if (expected.isDirectory()) expected.listDirectoryEntries().map { it.name }.toSet() else emptySet()
    val actualChildren = if (actual.isDirectory()) actual.listDirectoryEntries().map { it.name }.toSet() else emptySet()

    for (child in (expectedChildren - actualChildren)) {
      val actualChildPath = actual.resolve(child)
      val expectedChildPath = expected.resolve(child)
      if (expectedChildPath.isDirectory()) {
        // "Expected directory $expectedChild is missing in $actual"
        // proceed to report all files in there
        compareDirectories(expectedChildPath, actualChildPath)
      }
      else {
        actualChildPath.createParentDirectories().writeText("")
        softly.fail("Expected file $expectedChildPath is missing at $actualChildPath")
      }
    }

    for (child in (actualChildren - expectedChildren)) {
      val actualChildPath = actual.resolve(child)
      val expectedChildPath = expected.resolve(child)
      if (actualChildPath.isDirectory()) {
        // Actual directory $actualChild is unexpected at $expected
        // proceed to report all files in there
        compareDirectories(expectedChildPath, actualChildPath)
      }
      else {
        softly.fail("Actual file $actualChildPath is unexpected at $expectedChildPath")
      }
    }

    for (child in actualChildren.intersect(expectedChildren)) {
      val actualChildPath = actual.resolve(child)
      val expectedChildPath = expected.resolve(child)
      if (actualChildPath.isDirectory()) {
        // compare deeply
        compareDirectories(expectedChildPath, actualChildPath)
      }
      else {
        softly.assertThatCharSequence(actualChildPath.readText())
          .withFailMessage {
            "Actual file $actualChildPath is different from expected file at $expectedChildPath"
          }
          .isEqualTo(expectedChildPath.readText())
      }
    }
  }
}