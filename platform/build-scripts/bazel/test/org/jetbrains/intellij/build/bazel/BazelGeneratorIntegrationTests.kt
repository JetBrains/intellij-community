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
  @Test fun communitySubdirNaming() = doTest("community-subdir-naming")

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

    val bazelTargetsBeforeRunningGenerator = Files.createTempFile("bazel-targets-before-running-generator", ".json")
    JpsModuleToBazelTargetsOnly.main(
      arrayOf(
        "--manifest=${createManifest(tempDir)}",
        "--default-custom-modules=$defaultCustomModules",
        "--output=$bazelTargetsBeforeRunningGenerator",
        "--no-starlark-targets",
      )
    )

    compareDirectoriesWithoutMutation(projectDataPath, tempDir, "targets-only must not modify the project tree")
  
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

    val bazelTargetsAfterRunningGenerator = Files.createTempFile("bazel-targets-after-running-generator", ".json")
    JpsModuleToBazelTargetsOnly.main(
      arrayOf(
        "--manifest=${createManifest(tempDir)}",
        "--default-custom-modules=$defaultCustomModules",
        "--output=$bazelTargetsAfterRunningGenerator",
        "--no-starlark-targets",
      )
    )

    assertAndRemoveSameFiles(projectDataPath, tempDir)
    compareDirectories(expectedDataPath, tempDir)

    val bazelTargetsFromGenerator = tempDir.resolve("build").resolve("bazel-targets.json")
    softly.assertThat(bazelTargetsAfterRunningGenerator.readText())
      .withFailMessage {
        "Actual $bazelTargetsAfterRunningGenerator targets file is different from generated file at $bazelTargetsFromGenerator"
      }
      .isEqualTo(bazelTargetsFromGenerator.readText())
    softly.assertThat(bazelTargetsBeforeRunningGenerator.readText())
      .withFailMessage {
        "Actual $bazelTargetsBeforeRunningGenerator targets file is different from generated file at $bazelTargetsFromGenerator"
      }
      .isEqualTo(bazelTargetsFromGenerator.readText())

    // do not delete tempDir on tests failure, it is used in IDE to update expected file
    if (softly.wasSuccess()) {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun `MRI-4103 generator produces a diff if checkout directory is named main`() {
    val testName = "MRI-4103"

    val testDataPath = getTestDataPath(testName)

    val projectDataPath = testDataPath.resolve("project")
    assertTrue("$projectDataPath is not a directory", projectDataPath.isDirectory())

    val normalizedProjectDir = Files.createTempDirectory("project-$testName")
    projectDataPath.copyToRecursively(normalizedProjectDir, followLinks = true, overwrite = false)
    restoreBuildFileNames(normalizedProjectDir)

    val tempWorkspaceBaseDir = Files.createTempDirectory("test-$testName")
    val tempWorkspaceDir = tempWorkspaceBaseDir.resolve("main")
    Files.createDirectories(tempWorkspaceDir)
    val tempCommunityDir = tempWorkspaceDir.resolve("community")
    normalizedProjectDir.copyToRecursively(tempCommunityDir, followLinks = true, overwrite = false)

    JpsModuleToBazel.main(
      arrayOf(
        "--workspace_directory=$tempWorkspaceDir",
        "--run_without_ultimate_root=true",
        "--default-custom-modules=false",
        "--m2-repo=${testDataPath.resolve("m2-repo")}",
      )
    )

    assertFilesEqual(
      tempCommunityDir.resolve("BUILD.bazel"),
      normalizedProjectDir.resolve("BUILD.bazel"),
      "Generator should not modify existing project model files"
    )

    // do not delete tempDir on tests failure, it is used in IDE to update expected file
    if (softly.wasSuccess()) {
      tempWorkspaceBaseDir.deleteRecursively()
      normalizedProjectDir.deleteRecursively()
    }
  }

  private fun createManifest(projectDir: Path): Path {
    val manifest = Files.createTempFile("manifest", ".txt")
    val lines = mutableListOf<String>()
    for (path in projectDir.walk()) {
      if (path.isDirectory()) continue
      val relativePath = projectDir.relativize(path).toString()
      lines.add("copy\t${path.toAbsolutePath()}\t$relativePath")
    }
    manifest.writeText(lines.joinToString("\n"))
    return manifest
  }

  private fun getTestDataPath(testName: String): Path {
    return Path.of(requireEnv("TEST_SRCDIR"), requireEnv(TEST_DATA_MARKER_ENV)).parent.resolve(testName).normalize()
  }

  private fun requireEnv(name: String): String {
    val value = System.getenv(name)
    assertTrue("Missing $name env variable in bazel test environment", !value.isNullOrBlank())
    return value.orEmpty()
  }

  private fun restoreBuildFileNames(dir: Path) {
    val buildFilesToRestore = dir.walk()
      .filter { it.name == "${TILDE_BUILD_PREFIX}BUILD.bazel" }
      .toList()
    for (path in buildFilesToRestore) {
      path.moveTo(path.resolveSibling(path.name.removePrefix(TILDE_BUILD_PREFIX)))
    }
  }

  private fun assertFilesEqual(actualPath: Path, expectedPath: Path, messagePrefix: String) {
    val actualText = actualPath.readText()
    val expectedText = expectedPath.readText()
    if (actualText == expectedText) {
      return
    }

    softly.assertThat(false)
      .withFailMessage(buildFileMismatchMessage(messagePrefix, actualPath, expectedPath, actualText, expectedText))
      .isTrue()
  }

  private fun buildFileMismatchMessage(
    messagePrefix: String,
    actualPath: Path,
    expectedPath: Path,
    actualText: String,
    expectedText: String,
  ): String {
    val firstDifference = findFirstDifference(expectedText, actualText)
    return buildString {
      appendLine(messagePrefix)
      appendLine("Expected file: $expectedPath")
      appendLine("Actual file: $actualPath")
      appendLine(firstDifference)
      appendLine("Expected length=${expectedText.length}, trailing newline=${expectedText.endsWith('\n')}")
      appendLine("Actual length=${actualText.length}, trailing newline=${actualText.endsWith('\n')}")
      appendLine("--- Expected content ---")
      appendLine(renderTextWithLineNumbers(expectedText))
      appendLine("--- Actual content ---")
      append(renderTextWithLineNumbers(actualText))
    }
  }

  private fun findFirstDifference(expectedText: String, actualText: String): String {
    val firstDifferentIndex = expectedText.indices.firstOrNull { index -> expectedText[index] != actualText.getOrNull(index) }
                              ?: if (expectedText.length != actualText.length) minOf(expectedText.length, actualText.length) else null
    if (firstDifferentIndex == null) {
      return "First difference: none found"
    }

    val line = expectedText.take(firstDifferentIndex).count { it == '\n' } + 1
    val column = firstDifferentIndex - (expectedText.lastIndexOf('\n', firstDifferentIndex - 1).takeIf { it >= 0 } ?: -1)
    val expectedChar = expectedText.getOrNull(firstDifferentIndex).debugDisplay()
    val actualChar = actualText.getOrNull(firstDifferentIndex).debugDisplay()
    return "First difference at line $line, column $column: expected $expectedChar, actual $actualChar"
  }

  private fun renderTextWithLineNumbers(text: String): String {
    return text.lineSequence().mapIndexed { index, line -> "${index + 1}: $line" }.joinToString("\n").ifEmpty { "<empty>" }
  }

  private fun Char?.debugDisplay(): String = when (this) {
    null -> "<EOF>"
    '\n' -> "\\n"
    '\r' -> "\\r"
    '\t' -> "\\t"
    else -> "'$this'"
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
        assertFilesEqual(outputChildPath, initialProjectChildPath,
                         "Generator should not modify existing project model files")
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
        assertFilesEqual(actualChildPath, expectedChildPath,
                         "Generated output differs from expected snapshot")
      }
    }
  }

  private fun compareDirectoriesWithoutMutation(expected: Path, actual: Path, messagePrefix: String) {
    val expectedChildren = if (expected.isDirectory()) expected.listDirectoryEntries().map { it.name }.toSet() else emptySet()
    val actualChildren = if (actual.isDirectory()) actual.listDirectoryEntries().map { it.name }.toSet() else emptySet()

    softly.assertThat(expectedChildren - actualChildren)
      .describedAs("$messagePrefix: missing entries in $actual compared with $expected")
      .isEmpty()
    softly.assertThat(actualChildren - expectedChildren)
      .describedAs("$messagePrefix: unexpected entries in $actual compared with $expected")
      .isEmpty()

    for (child in actualChildren.intersect(expectedChildren)) {
      val actualChildPath = actual.resolve(child)
      val expectedChildPath = expected.resolve(child)
      if (actualChildPath.isDirectory()) {
        compareDirectoriesWithoutMutation(expectedChildPath, actualChildPath, messagePrefix)
      }
      else {
        assertFilesEqual(actualChildPath, expectedChildPath, messagePrefix)
      }
    }
  }
}
