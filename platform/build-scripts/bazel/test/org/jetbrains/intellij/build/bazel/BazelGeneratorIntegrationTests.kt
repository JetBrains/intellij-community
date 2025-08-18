// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.application.PathManager
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.rt.execution.junit.FileComparisonFailure
import org.assertj.core.api.SoftAssertions
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentest4j.AssertionFailedError
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText


@OptIn(ExperimentalPathApi::class)
@ExtendWith(SoftAssertionsExtension::class)
class BazelGeneratorIntegrationTests {
  @InjectSoftAssertions
  lateinit var softly: SoftAssertions

  @Test fun snapshotRepositoryLibrary() = doTest("snapshot-repository-library")
  @Test fun snapshotLibrary() = doTest("snapshot-library")
  @Test fun snapshotLibraryInTree() = doTest("snapshot-library-in-tree")

  private fun doTest(
    testName: String,
    runWithoutUltimateRoot: Boolean = true,
    defaultCustomModules: Boolean = false,
  ) {
    val testDataPath = Path.of(
      PathManager.getCommunityHomePath(),
      "platform/build-scripts/bazel/testData/integration/$testName"
    )

    val projectDataPath = testDataPath.resolve("project")
    check(projectDataPath.isDirectory()) {
      "$projectDataPath is not a directory"
    }

    val expectedDataPath = testDataPath.resolve("expected")
    check(expectedDataPath.isDirectory()) {
      "$expectedDataPath is not a directory"
    }

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

    assertAndRemoveSameFiles(projectDataPath, tempDir)
    compareDirectories(expectedDataPath, tempDir)

    if (!softly.wasSuccess()) {
      // do not delete tempDir on tests failure, it is used in IDE to update expected file
      tempDir.deleteRecursively()
    }
  }

  private fun assertAndRemoveSameFiles(initialProjectDir: Path, testOutputDir: Path) {
    val initialProjectFiles = initialProjectDir.listDirectoryEntries()

    for (initialProjectChildPath in initialProjectFiles) {
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
        softly.collectAssertionError(
          FileComparisonFailedError(
            message = "Actual file $outputChildPath is different from initial project file at $initialProjectChildPath. Generator should not modify existing project model files",
            expected = initialProjectChildPath.readText(),
            expectedFilePath = initialProjectChildPath.toString(),
            actual = outputChildPath.readText(),
            actualFilePath = outputChildPath.toString(),
          )
        )
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
        actualChildPath.writeText("")
        softly.collectAssertionError(
          FileComparisonFailedError(
            message = "Expected file $expectedChildPath is missing at $actualChildPath",
            expected = expectedChildPath.readText(),
            expectedFilePath = expectedChildPath.toString(),
            actual = "",
            actualFilePath = actualChildPath.toString(),
          )
        )
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
        softly.collectAssertionError(
          AssertionFailedError("Actual file $actualChildPath is unexpected at $expectedChildPath"))
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
        if (actualChildPath.readText() != expectedChildPath.readText()) {
          // for some reason, adding it to softly or using non-deprecated FileComparisonFailedError
          // does not show a diff in IDEA
          throw FileComparisonFailure(
            "Actual file $actualChildPath is different from $expectedChildPath",
            expectedChildPath.readText(),
            actualChildPath.readText(),
            expectedChildPath.toString(),
            actualChildPath.toString(),
          )
        }
      }
    }
  }
}