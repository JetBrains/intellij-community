// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.assertj.core.api.JUnitSoftAssertions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
  @Test fun kotlinSnapshotModuleLibrary() = doTest("kotlin-snapshot-module-library")
  @Test fun snapshotRepositoryLibrary() = doTest("snapshot-repository-library")
  @Test fun snapshotLibrary() = doTest("snapshot-library")
  @Test fun snapshotLibraryInTree() = doTest("snapshot-library-in-tree")
  @Test fun moduleRepositoryLibrarySnapshot() = doTest("module-repository-library-snapshot")
  @Test fun communitySubdirNaming() = doTest("community-subdir-naming")

  @Test fun resourcesSingleRoot() = doTest("resources-single-root")
  @Test fun resourcesMultipleRoots() = doTest("resources-multiple-roots")
  @Test fun resourcesNonPluginXmlIgnored() = doTest("resources-non-plugin-xml-ignored")
  @Test fun resourcesTestRoot() = doTest("resources-test-root")
  @Test fun resourcesPluginDescriptorInSecondRoot() = doTest("resources-plugin-descriptor-in-second-root")

  @Test fun compileExcludes() = doTest("compile-excludes")

  @Test fun ijPluginTarget() = doTest("ij-plugin-target")
  @Test fun ijPluginTargetWithNonClasspathData() = doTest("ij-plugin-target-with-non-classpath-data", allowBuildBazelFilesInTestDataProject = true)

  @Test
  fun `modules in the same package do not generate BUILD metadata`() {
    val projectDir = createMetadataProject()
    addMetadataModule(projectDir, "module", "intellij.second")
    generateMetadataProject(projectDir)

    val buildFile = projectDir.resolve("module/BUILD.bazel")
    val firstContent = buildFile.readText()
    softly.assertThat(firstContent)
      .contains("### auto-generated section `iml intellij.module` start")
      .contains("### auto-generated section `iml intellij.second` start")
    val targets = projectDir.resolve("build/bazel-targets.json").readText()

    generateMetadataProject(projectDir)
    assertEquals(firstContent, buildFile.readText())
    assertEquals(targets, projectDir.resolve("build/bazel-targets.json").readText())
    if (softly.wasSuccess()) projectDir.deleteRecursively()
  }

  @Test
  fun `skipped compilation and source exports do not generate BUILD metadata`() {
    val projectDir = createMetadataProject()
    val buildFile = projectDir.resolve("module/BUILD.bazel")
    val handwritten = """
      ### skip generation section `build intellij.module`
      ### skip generation section `iml intellij.module`
      ### skip generation section `test intellij.module`
      exports_files(["BUILD.bazel"], visibility = ["//allowed:__pkg__"])
      filegroup(name = "module", srcs = ["module.jar"])
    """.trimIndent()
    buildFile.writeText(handwritten + "\n")

    generateMetadataProject(projectDir)

    assertEquals(handwritten + "\n", buildFile.readText())
    projectDir.deleteRecursively()
  }

  @Test
  fun `community ultimate and nested package labels do not need BUILD metadata`() {
    val projectDir = Files.createTempDirectory("build-metadata-ultimate")
    projectDir.resolve(".ultimate.root.marker").writeText("")
    val communityDir = projectDir.resolve("community")
    getTestDataPath("MRI-4552").resolve("project").copyToRecursively(communityDir, followLinks = true, overwrite = false)
    communityDir.resolve(".idea").copyToRecursively(projectDir.resolve(".idea"), followLinks = true, overwrite = false)
    communityDir.resolve("module").copyToRecursively(projectDir.resolve("module"), followLinks = true, overwrite = false)
    val modulesFile = projectDir.resolve(".idea/modules.xml")
    modulesFile.writeText(modulesFile.readText().replace("\$/module/", "\$/community/module/"))
    projectDir.resolve("module/intellij.module.iml").deleteExisting()
    addMetadataModule(projectDir, "module", "intellij.ultimate.module", communityDir)
    val standaloneModules = listOf(
      "community/platform/build-scripts/bazel" to "intellij.platform.buildScripts.bazel",
      "community/build/jvm-rules/jvm-inc-builder" to "intellij.tools.build.bazel.jvmIncBuilder",
      "community/build/jvm-rules/jvm-inc-builder" to "intellij.tools.build.bazel.jvmIncBuilderTests",
    )
    for ((directory, moduleName) in standaloneModules) {
      addMetadataModule(projectDir, directory, moduleName, communityDir)
      projectDir.resolve("$directory/BUILD.bazel").writeText("filegroup(name = \"handwritten\")\n")
    }

    generateMetadataProject(projectDir, communityOnly = false)

    val index = projectDir.resolve("build/bazel-targets.json").readText()
    softly.assertThat(index)
      .contains("@community//module:intellij.module.iml")
      .contains("//module:intellij.ultimate.module.iml")
      .contains("@jps_to_bazel//:intellij.platform.buildScripts.bazel.iml")
      .contains("@rules_jvm//jvm-inc-builder:intellij.tools.build.bazel.jvmIncBuilder.iml")
    val generatedFileList = communityDir.resolve(BAZEL_GENERATED_FILE_LIST_RELATIVE_PATH).readText()
    for (directory in standaloneModules.map { it.first }.distinct()) {
      assertEquals("filegroup(name = \"handwritten\")\n", projectDir.resolve("$directory/BUILD.bazel").readText())
      softly.assertThat(generatedFileList).doesNotContain(directory.removePrefix("community/"))
    }
    softly.assertThat(communityDir.resolve("module/BUILD.bazel").readText()).contains("### auto-generated section `iml intellij.module` start")
    softly.assertThat(projectDir.resolve("module/BUILD.bazel").readText()).contains("### auto-generated section `iml intellij.ultimate.module` start")
    if (softly.wasSuccess()) projectDir.deleteRecursively()
  }

  @Test
  fun `missing skipped module owner is rejected before saving`() {
    val projectDir = createMetadataProject()
    addMetadataModule(projectDir, "platform/build-scripts/bazel", "intellij.platform.buildScripts.bazel")
    val exception = assertThrows(IllegalStateException::class.java) {
      generateMetadataProject(projectDir)
    }
    softly.assertThat(exception.message)
      .contains("Missing original BUILD.bazel owner for skipped module intellij.platform.buildScripts.bazel")
      .contains(projectDir.resolve("platform/build-scripts/bazel/BUILD.bazel").toString())
    assertTrue("The generator must not save module files when a skipped owner is missing", !Files.exists(projectDir.resolve("module/BUILD.bazel")))
    if (softly.wasSuccess()) projectDir.deleteRecursively()
  }

  @Test
  fun `BUILD cleanup retains a skipped owner from a stale manifest`() {
    val projectDir = createMetadataProject()
    val directory = "platform/build-scripts/bazel"
    addMetadataModule(projectDir, directory, "intellij.platform.buildScripts.bazel")
    val owner = projectDir.resolve("$directory/BUILD.bazel")
    owner.writeText("filegroup(name = \"handwritten\")\n")
    repeat(3) {
      generateMetadataProject(projectDir)
      assertEquals("filegroup(name = \"handwritten\")\n", owner.readText())
    }

    val obsoleteOwner = projectDir.resolve("obsolete/BUILD.bazel")
    obsoleteOwner.createParentDirectories().writeText("filegroup(name = \"obsolete\")\n")
    val manifest = projectDir.resolve(BAZEL_GENERATED_FILE_LIST_RELATIVE_PATH)
    manifest.writeText("module\n$directory\nobsolete\n")
    repeat(2) {
      generateMetadataProject(projectDir)
      assertEquals("filegroup(name = \"handwritten\")\n", owner.readText())
      assertEquals("module", manifest.readText())
      assertTrue("The generator must still delete an obsolete owner", !Files.exists(obsoleteOwner))
    }
    projectDir.deleteRecursively()
  }

  @Test
  fun `BUILD cleanup retains an owner that changes from generated to skipped`() {
    val projectDir = createMetadataProject()
    generateMetadataProject(projectDir)
    val owner = projectDir.resolve("module/BUILD.bazel")
    val original = owner.readText()
    val manifest = projectDir.resolve(BAZEL_GENERATED_FILE_LIST_RELATIVE_PATH)
    assertEquals("module", manifest.readText())

    val newName = "intellij.platform.buildScripts.bazel.iml"
    projectDir.resolve("module/intellij.module.iml").moveTo(projectDir.resolve("module/$newName"))
    val modules = projectDir.resolve(".idea/modules.xml")
    modules.writeText(modules.readText().replace("intellij.module.iml", newName))
    repeat(2) {
      generateMetadataProject(projectDir)
      assertEquals(original, owner.readText())
      assertEquals("", manifest.readText())
    }
    projectDir.deleteRecursively()
  }

  private fun createMetadataProject(): Path {
    val projectDir = Files.createTempDirectory("build-metadata")
    getTestDataPath("MRI-4552").resolve("project").copyToRecursively(projectDir, followLinks = true, overwrite = false)
    return projectDir
  }

  private fun addMetadataModule(projectDir: Path, directory: String, moduleName: String, templateDir: Path = projectDir) {
    val relativePath = "$directory/$moduleName.iml"
    projectDir.resolve(relativePath).createParentDirectories().writeText(templateDir.resolve("module/intellij.module.iml").readText())
    val modulesFile = projectDir.resolve(".idea/modules.xml")
    val entry = "<module fileurl=\"file://\$PROJECT_DIR\$/$relativePath\" filepath=\"\$PROJECT_DIR\$/$relativePath\" />"
    modulesFile.writeText(modulesFile.readText().replace("</modules>", "$entry\n    </modules>"))
  }

  private fun generateMetadataProject(projectDir: Path, communityOnly: Boolean = true) {
    JpsModuleToBazel.main(
      arrayOf(
        "--workspace_directory=$projectDir",
        "--run_without_ultimate_root=$communityOnly",
        "--default-custom-modules=false",
        "--m2-repo=${projectDir.resolve("m2-repo")}",
      )
    )
    for (buildFile in projectDir.walk().filter { it.name == "BUILD.bazel" }) {
      softly.assertThat(buildFile.readText())
        .describedAs("No BUILD metadata helper or section in $buildFile")
        .doesNotContain("jps_model_build_file", "### auto-generated section `jps model build file`")
    }
  }

  /**
   * The content modules of an `ij_plugin` plugin, with and without module libraries to pack.
   *
   * `intellij.libraries.example` declares three production module libraries and one TEST-scope library. The three
   * become the `packed_deps` of its `ij_plugin_module`, in `orderEntry` order, and the TEST-scope library stays a
   * runtime dependency of the test target alone. `intellij.libraries.refused` declares only the TEST-scope library, so
   * it has nothing to pack and stays a plain `jvm_library`.
   */
  @Test fun ijPluginModulePackedDeps() = doTest("ij-plugin-module-packed-deps")

  private fun doTest(
    testName: String,
    runWithoutUltimateRoot: Boolean = true,
    defaultCustomModules: Boolean = false,
    allowBuildBazelFilesInTestDataProject: Boolean = false,
  ) {
    val testDataPath = getTestDataPath(testName)

    val projectDataPath = testDataPath.resolve("project")
    assertTrue("$projectDataPath is not a directory", projectDataPath.isDirectory())
    if (!allowBuildBazelFilesInTestDataProject) {
      for (path in projectDataPath.walk()) {
        check(!path.name.startsWith(TILDE_BUILD_PREFIX)) {
          "Initial project dir is not expected to contain $TILDE_BUILD_PREFIX files, but $path is"
        }
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

    if (allowBuildBazelFilesInTestDataProject) {
      for (path in tempDir.walk()) {
        if (path.name.startsWith(TILDE_BUILD_PREFIX)) {
          path.moveTo(path.resolveSibling(path.name.removePrefix(TILDE_BUILD_PREFIX)))
        }
      }
    }

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

    assertAndRemoveSameFiles(projectDataPath, tempDir, allowBuildBazelFilesInTestDataProject)
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

  @Test
  fun `MRI-4552 generator emits jps_test load for a module with test sources`() {
    val testName = "MRI-4552"
    val testDataPath = getTestDataPath(testName)

    val projectDataPath = testDataPath.resolve("project")
    assertTrue("$projectDataPath is not a directory", projectDataPath.isDirectory())

    val tempDir = Files.createTempDirectory("test-$testName")
    projectDataPath.copyToRecursively(tempDir, followLinks = true, overwrite = false)

    JpsModuleToBazel.main(
      arrayOf(
        "--workspace_directory=$tempDir",
        "--run_without_ultimate_root=true",
        "--default-custom-modules=false",
        "--m2-repo=${tempDir.resolve("m2-repo")}",
      )
    )

    val generatedBuildFile = tempDir.resolve("module").resolve("BUILD.bazel")
    assertTrue("Generated $generatedBuildFile is missing", Files.exists(generatedBuildFile))
    val generatedContent = generatedBuildFile.readText()

    // The module declares a test source root, so a jps_test target must be generated.
    softly.assertThat(generatedContent)
      .describedAs("jps_test target must be generated for a module with test sources")
      .contains("jps_test(")
    // The matching load statement must be present as well, otherwise Bazel fails with
    // "name 'jps_test' is not defined" when the BUILD.bazel is freshly generated (MRI-4552).
    softly.assertThat(generatedContent)
      .describedAs("jps_test load statement must be present so the generated BUILD.bazel compiles")
      .contains("""load("@community//build:tests-options.bzl", "jps_test")""")

    // do not delete tempDir on tests failure, it is used in IDE to inspect the generated output
    if (softly.wasSuccess()) {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun `MRI-4647 generator translates -Werror facet option to warn = error`() {
    val testName = "MRI-4647"
    val testDataPath = getTestDataPath(testName)

    val projectDataPath = testDataPath.resolve("project")
    assertTrue("$projectDataPath is not a directory", projectDataPath.isDirectory())

    val tempDir = Files.createTempDirectory("test-$testName")
    projectDataPath.copyToRecursively(tempDir, followLinks = true, overwrite = false)

    JpsModuleToBazel.main(
      arrayOf(
        "--workspace_directory=$tempDir",
        "--run_without_ultimate_root=true",
        "--default-custom-modules=false",
        "--m2-repo=${tempDir.resolve("m2-repo")}",
      )
    )

    val generatedBuildFile = tempDir.resolve("module").resolve("BUILD.bazel")
    assertTrue("Generated $generatedBuildFile is missing", Files.exists(generatedBuildFile))
    val generatedContent = generatedBuildFile.readText()

    // The module's Kotlin facet sets -Werror (allWarningsAsErrors), so a custom kotlinc options
    // target must be generated for it.
    softly.assertThat(generatedContent)
      .describedAs("create_kotlinc_options target must be generated for a module with a -Werror facet option")
      .contains("create_kotlinc_options(")
    // -Werror must be translated to warn = "error", which the JPS incremental worker turns back into -Werror.
    softly.assertThat(generatedContent)
      .describedAs("-Werror facet option must be translated to warn = \"error\"")
      .contains("warn = \"error\"")

    // do not delete tempDir on tests failure, it is used in IDE to inspect the generated output
    if (softly.wasSuccess()) {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun `MRI-4701 generator translates -Xwarning-level facet option to x_warning_level`() {
    val testName = "MRI-4701"
    val testDataPath = getTestDataPath(testName)

    val projectDataPath = testDataPath.resolve("project")
    assertTrue("$projectDataPath is not a directory", projectDataPath.isDirectory())

    val tempDir = Files.createTempDirectory("test-$testName")
    projectDataPath.copyToRecursively(tempDir, followLinks = true, overwrite = false)

    JpsModuleToBazel.main(
      arrayOf(
        "--workspace_directory=$tempDir",
        "--run_without_ultimate_root=true",
        "--default-custom-modules=false",
        "--m2-repo=${tempDir.resolve("m2-repo")}",
      )
    )

    val generatedBuildFile = tempDir.resolve("module").resolve("BUILD.bazel")
    assertTrue("Generated $generatedBuildFile is missing", Files.exists(generatedBuildFile))
    val generatedContent = generatedBuildFile.readText()

    // The module's Kotlin facet sets -Xwarning-level=DEPRECATION:warning, so a custom kotlinc options
    // target must be generated for it.
    softly.assertThat(generatedContent)
      .describedAs("create_kotlinc_options target must be generated for a module with a -Xwarning-level facet option")
      .contains("create_kotlinc_options(")
    // -Xwarning-level must be translated to x_warning_level, which the JPS incremental worker turns back into -Xwarning-level.
    softly.assertThat(generatedContent)
      .describedAs("-Xwarning-level facet option must be translated to x_warning_level entries")
      .contains("x_warning_level = [")
    softly.assertThat(generatedContent)
      .describedAs("the DEPRECATION:warning level must be preserved")
      .contains("\"DEPRECATION:warning\"")
    // the facet also sets -Werror, which must keep working alongside the per-diagnostic override.
    softly.assertThat(generatedContent)
      .describedAs("-Werror facet option must still be translated to warn = \"error\"")
      .contains("warn = \"error\"")

    // do not delete tempDir on tests failure, it is used in IDE to inspect the generated output
    if (softly.wasSuccess()) {
      tempDir.deleteRecursively()
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

  private fun assertAndRemoveSameFiles(initialProjectDir: Path, testOutputDir: Path, allowBuildBazelFilesInTestDataProject: Boolean) {
    for (initialProjectChildPath in initialProjectDir.listDirectoryEntries()) {
      val outputChildPath = testOutputDir.resolve(initialProjectChildPath.name)
      if (initialProjectChildPath.isDirectory()) {
        check(outputChildPath.isDirectory()) {
           "$outputChildPath is not a directory, but $initialProjectChildPath is"
        }

        assertAndRemoveSameFiles(initialProjectChildPath, outputChildPath, allowBuildBazelFilesInTestDataProject)
      }
      else if (outputChildPath.readText() == initialProjectChildPath.readText()) {
        outputChildPath.deleteExisting()
      }
      else if (!(allowBuildBazelFilesInTestDataProject && initialProjectChildPath.name.startsWith(TILDE_BUILD_PREFIX))) {
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
