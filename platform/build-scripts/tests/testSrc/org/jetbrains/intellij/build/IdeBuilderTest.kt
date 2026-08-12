// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.configureDevModeBuildOptions
import org.jetbrains.intellij.build.dev.copyWithDevBuildOverrides
import org.jetbrains.intellij.build.dev.createDevBuildPaths
import org.jetbrains.intellij.build.dev.formatCoreClasspath
import org.jetbrains.intellij.build.dev.prepareOverriddenRunDir
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

class IdeBuilderTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun createProjectDevBuildOptionsUsesRequestClassesOutputDirectoryOverride() {
    val requestClassesOutputDirectory = tempDir.resolve("request-classes")
    val options = createProjectDevBuildOptions(
      request = createBuildRequest(classesOutputDirectory = requestClassesOutputDirectory),
      buildDir = tempDir.resolve("dev-build"),
      buildOptionsTemplate = BuildOptions(
        useCompiledClassesFromProjectOutput = true,
        classOutDir = tempDir.resolve("template-classes").toString(),
      ),
    )

    assertThat(options.useCompiledClassesFromProjectOutput).isTrue()
    assertThat(options.classOutDir).isEqualTo(requestClassesOutputDirectory.toString())
    assertThat(options.pathToCompiledClassesArchive).isNull()
    assertThat(options.pathToCompiledClassesArchivesMetadata).isNull()
    assertThat(options.unpackCompiledClassesArchives).isTrue()
  }

  @Test
  fun createProjectDevBuildOptionsPreservesArchiveBackedTemplate() {
    val classOutDir = tempDir.resolve("archive-backed-classes")
    val archivePath = tempDir.resolve("compiled-classes.zip")
    val metadataPath = tempDir.resolve("compiled-classes-metadata.json")
    val options = createProjectDevBuildOptions(
      request = createBuildRequest(),
      buildDir = tempDir.resolve("dev-build"),
      buildOptionsTemplate = BuildOptions(
        useCompiledClassesFromProjectOutput = false,
        classOutDir = classOutDir.toString(),
        pathToCompiledClassesArchive = archivePath,
        pathToCompiledClassesArchivesMetadata = metadataPath,
        unpackCompiledClassesArchives = false,
        useTestCompilationOutput = true,
      ).apply {
        buildNumber = "241.1"
        isInDevelopmentMode = false
        isTestBuild = true
      },
    )

    assertThat(options.useCompiledClassesFromProjectOutput).isFalse()
    assertThat(options.classOutDir).isEqualTo(classOutDir.toString())
    assertThat(options.pathToCompiledClassesArchive).isEqualTo(archivePath)
    assertThat(options.pathToCompiledClassesArchivesMetadata).isEqualTo(metadataPath)
    assertThat(options.unpackCompiledClassesArchives).isFalse()
    assertThat(options.useTestCompilationOutput).isTrue()
    assertThat(options.buildNumber).isEqualTo("241.1")
    assertThat(options.isInDevelopmentMode).isFalse()
    assertThat(options.isTestBuild).isTrue()
  }

  @Test
  fun configureDevModeBuildOptionsDisablesGitRevision() {
    val options = BuildOptions().apply {
      storeGitRevision = true
    }

    configureDevModeBuildOptions(
      options = options,
      request = createBuildRequest(),
      buildOptionsTemplate = BuildOptions(),
    )

    assertThat(options.storeGitRevision).isFalse()
  }

  @Test
  fun createProjectDevBuildOptionsUsesRequestBuildDateOverride() {
    val buildDateInSeconds = 1_700_000_000L
    val options = createProjectDevBuildOptions(
      request = createBuildRequest(buildDateInSeconds = buildDateInSeconds),
      buildDir = tempDir.resolve("dev-build"),
      buildOptionsTemplate = BuildOptions(),
    )

    assertThat(options.buildDateInSeconds).isEqualTo(buildDateInSeconds)
  }

  @Test
  fun createProjectDevBuildOptionsFallsBackToDevModeBuildDate() {
    val options = createProjectDevBuildOptions(
      request = createBuildRequest(),
      buildDir = tempDir.resolve("dev-build"),
      buildOptionsTemplate = BuildOptions(),
    )

    assertThat(options.buildDateInSeconds).isEqualTo(getDevModeOrTestBuildDateInSeconds())
  }

  @Test
  fun configureDevModeBuildOptionsLinksImmutableCacheEntriesByDefault() {
    val options = BuildOptions().apply {
      linkImmutableCacheEntries = false
    }

    configureDevModeBuildOptions(
      options = options,
      request = createBuildRequest(),
      buildOptionsTemplate = BuildOptions(),
    )

    assertThat(options.linkImmutableCacheEntries).isTrue()
  }

  @Test
  fun configureDevModeBuildOptionsKeepsImmutableCacheEntryLinkingDisabledOnRequest() {
    val options = BuildOptions().apply {
      linkImmutableCacheEntries = true
    }

    configureDevModeBuildOptions(
      options = options,
      request = createBuildRequest(linkImmutableCacheEntries = false),
      buildOptionsTemplate = BuildOptions(),
    )

    assertThat(options.linkImmutableCacheEntries).isFalse()
  }

  // `createDevModeProductRunner` builds its options from an enclosing real build instead of from the project model.
  // It used to carry its own copy of the override list, and that copy silently dropped the request's build date.
  @Test
  fun copyWithDevBuildOverridesKeepsTheEnclosingBuildDateWhenTheRequestHasNone() {
    val enclosingBuildDateInSeconds = 1_600_000_000L

    val options = BuildOptions(buildDateInSeconds = enclosingBuildDateInSeconds).copyWithDevBuildOverrides(
      request = createBuildRequest(),
      buildDir = tempDir.resolve("dev-build"),
      defaultBuildDateInSeconds = enclosingBuildDateInSeconds,
    )

    assertThat(options.buildDateInSeconds).isEqualTo(enclosingBuildDateInSeconds)
  }

  @Test
  fun copyWithDevBuildOverridesAppliesTheRequestBuildDateOverTheEnclosingOne() {
    val enclosingBuildDateInSeconds = 1_600_000_000L
    val requestedBuildDateInSeconds = 1_700_000_000L

    val options = BuildOptions(buildDateInSeconds = enclosingBuildDateInSeconds).copyWithDevBuildOverrides(
      request = createBuildRequest(buildDateInSeconds = requestedBuildDateInSeconds),
      buildDir = tempDir.resolve("dev-build"),
      defaultBuildDateInSeconds = enclosingBuildDateInSeconds,
    )

    assertThat(options.buildDateInSeconds).isEqualTo(requestedBuildDateInSeconds)
  }

  @Test
  fun prepareOverriddenRunDirCreatesAnAbsentDirectory() {
    val runDir = tempDir.resolve("dist")

    val result = runBlocking { prepareOverriddenRunDir(runDir) }

    assertThat(result).isEqualTo(runDir)
    assertThat(Files.isDirectory(runDir)).isTrue()
  }

  @Test
  fun prepareOverriddenRunDirAcceptsAnEmptyDirectory() {
    val runDir = Files.createDirectories(tempDir.resolve("dist"))

    assertThat(runBlocking { prepareOverriddenRunDir(runDir) }).isEqualTo(runDir)
  }

  @Test
  fun prepareOverriddenRunDirRejectsStaleContent() {
    val runDir = Files.createDirectories(tempDir.resolve("dist"))
    Files.writeString(runDir.resolve("core-classpath.txt"), "lib/stale.jar")

    assertThatThrownBy { runBlocking { prepareOverriddenRunDir(runDir) } }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("core-classpath.txt")
  }

  @Test
  fun createProjectDevBuildOptionsPutsLogDirUnderScratchDir() {
    val scratchDir = tempDir.resolve("scratch")
    val options = createProjectDevBuildOptions(
      request = createBuildRequest(scratchDir = scratchDir),
      buildDir = tempDir.resolve("dev-build"),
      buildOptionsTemplate = BuildOptions(),
    )

    assertThat(options.logDir).isEqualTo(scratchDir.resolve("log"))
  }

  @Test
  fun createProjectDevBuildOptionsPutsLogDirUnderBuildDirWithoutScratchDir() {
    val buildDir = tempDir.resolve("dev-build")
    val options = createProjectDevBuildOptions(
      request = createBuildRequest(),
      buildDir = buildDir,
      buildOptionsTemplate = BuildOptions(),
    )

    assertThat(options.logDir).isEqualTo(buildDir.resolve("log"))
  }

  @Test
  fun createDevBuildPathsKeepsScratchDataOutOfTheDistributionDirectory() {
    val buildDir = tempDir.resolve("dist")
    val scratchDir = tempDir.resolve("scratch")

    val paths = createDevBuildPaths(
      projectDir = COMMUNITY_ROOT.communityRoot,
      buildDir = buildDir,
      logDir = scratchDir.resolve("log"),
      scratchDir = scratchDir,
    )

    assertThat(paths.tempDir).isEqualTo(scratchDir.resolve("temp"))
    assertThat(paths.artifactDir).isEqualTo(scratchDir.resolve("artifacts"))
    assertThat(paths.buildOutputDir).isEqualTo(buildDir)
    assertThat(paths.distAllDir).isEqualTo(buildDir)
    assertThat(Files.exists(buildDir)).isFalse()
  }

  @Test
  fun createDevBuildPathsRootsScratchDataInBuildDirByDefault() {
    val buildDir = tempDir.resolve("dev-build")

    val paths = createDevBuildPaths(projectDir = COMMUNITY_ROOT.communityRoot, buildDir = buildDir, logDir = buildDir.resolve("log"))

    assertThat(paths.tempDir).isEqualTo(buildDir.resolve("temp"))
    assertThat(paths.artifactDir).isEqualTo(buildDir.resolve("artifacts"))
  }

  @Test
  fun createProjectDevBuildOptionsTreatsUnpackFlagAsArchivePolicyOnly() {
    val classOutDir = tempDir.resolve("classes")
    val options = createProjectDevBuildOptions(
      request = createBuildRequest(),
      buildDir = tempDir.resolve("dev-build"),
      buildOptionsTemplate = BuildOptions(
        useCompiledClassesFromProjectOutput = true,
        classOutDir = classOutDir.toString(),
        pathToCompiledClassesArchive = tempDir.resolve("compiled-classes.zip"),
        pathToCompiledClassesArchivesMetadata = tempDir.resolve("compiled-classes-metadata.json"),
        unpackCompiledClassesArchives = false,
      ),
    )

    assertThat(options.useCompiledClassesFromProjectOutput).isTrue()
    assertThat(options.classOutDir).isEqualTo(classOutDir.toString())
    assertThat(options.pathToCompiledClassesArchive).isNull()
    assertThat(options.pathToCompiledClassesArchivesMetadata).isNull()
    assertThat(options.unpackCompiledClassesArchives).isTrue()
  }

  @Test
  fun createProjectDevBuildOptionsUsesCapturedBuildOptionsTemplate() {
    val originalUseCompiledClasses = System.getProperty(BuildOptions.USE_COMPILED_CLASSES_PROPERTY)
    val originalCompiledClassesArchive = System.getProperty(BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE)
    val originalCompiledClassesArchivesMetadata = System.getProperty(BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA)
    val originalUnpackCompiledClassesArchives = System.getProperty(BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_UNPACK)
    val originalProjectClassesOutputDirectory = System.getProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY)

    val archivePath = Files.createFile(tempDir.resolve("compiled-classes.zip"))
    val metadataPath = Files.createFile(tempDir.resolve("compiled-classes-metadata.json"))
    val archiveBackedClassesOutput = tempDir.resolve("archive-backed-classes")

    System.setProperty(BuildOptions.USE_COMPILED_CLASSES_PROPERTY, "false")
    System.setProperty(BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE, archivePath.toString())
    System.setProperty(BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA, metadataPath.toString())
    System.setProperty(BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_UNPACK, "false")
    System.setProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY, archiveBackedClassesOutput.toString())
    try {
      val buildOptionsTemplate = BuildOptions()

      System.setProperty(BuildOptions.USE_COMPILED_CLASSES_PROPERTY, "true")
      System.clearProperty(BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE)
      System.clearProperty(BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA)
      System.clearProperty(BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_UNPACK)
      System.setProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY, tempDir.resolve("different-classes").toString())

      val options = createProjectDevBuildOptions(
        request = createBuildRequest(),
        buildDir = tempDir.resolve("dev-build"),
        buildOptionsTemplate = buildOptionsTemplate,
      )

      assertThat(options.useCompiledClassesFromProjectOutput).isFalse()
      assertThat(options.classOutDir).isEqualTo(archiveBackedClassesOutput.toString())
      assertThat(options.pathToCompiledClassesArchive).isEqualTo(archivePath)
      assertThat(options.pathToCompiledClassesArchivesMetadata).isEqualTo(metadataPath)
      assertThat(options.unpackCompiledClassesArchives).isFalse()
    }
    finally {
      restoreSystemProperty(BuildOptions.USE_COMPILED_CLASSES_PROPERTY, originalUseCompiledClasses)
      restoreSystemProperty(BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE, originalCompiledClassesArchive)
      restoreSystemProperty(BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA, originalCompiledClassesArchivesMetadata)
      restoreSystemProperty(BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_UNPACK, originalUnpackCompiledClassesArchives)
      restoreSystemProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY, originalProjectClassesOutputDirectory)
    }
  }

  @Test
  fun buildServerConfigurationLoadingDoesNotMutateProjectClassesOutputProperty() {
    val propertyName = BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY
    val originalValue = System.getProperty(propertyName)
    val sentinelValue = tempDir.resolve("existing-classes-output").toString()

    System.setProperty(propertyName, sentinelValue)
    try {
      createConfiguration(COMMUNITY_ROOT.communityRoot)

      assertThat(System.getProperty(propertyName)).isEqualTo(sentinelValue)
    }
    finally {
      restoreSystemProperty(propertyName, originalValue)
    }
  }

  @Test
  fun productionAndTestClassesOutputDirectoriesFollowStandardJpsLayout() {
    val classesOutputDirectory = tempDir.resolve("classes")

    assertThat(getProductionClassesOutputDirectory(classesOutputDirectory)).isEqualTo(classesOutputDirectory.resolve("production"))
    assertThat(getTestClassesOutputDirectory(classesOutputDirectory)).isEqualTo(classesOutputDirectory.resolve("test"))
  }

  @Test
  fun formatCoreClasspathWritesEntriesUnderRunDirAsRelativePaths() {
    val runDir = tempDir.resolve("run")

    assertThat(formatCoreClasspath(listOf(runDir.resolve("lib/util.jar")), runDir)).isEqualTo("lib/util.jar")
  }

  @Test
  fun formatCoreClasspathUsesForwardSlashesRegardlessOfOs() {
    val runDir = tempDir.resolve("run")
    val classPathString = formatCoreClasspath(listOf(runDir.resolve("lib").resolve("modules").resolve("util.jar")), runDir)

    assertThat(classPathString).isEqualTo("lib/modules/util.jar")
    assertThat(classPathString).doesNotContain("\\")
  }

  @Test
  fun formatCoreClasspathKeepsEntriesOutsideRunDirAbsolute() {
    val runDir = tempDir.resolve("run")
    val jarCacheEntry = tempDir.resolve("jar-cache").resolve("payload.jar")

    assertThat(formatCoreClasspath(listOf(jarCacheEntry), runDir)).isEqualTo(jarCacheEntry.invariantSeparatorsPathString)
  }

  @Test
  fun formatCoreClasspathJoinsEntriesByNewlineInInputOrder() {
    val runDir = tempDir.resolve("run")
    val outsideEntry = tempDir.resolve("jar-cache").resolve("payload.jar")

    val classPathString = formatCoreClasspath(
      listOf(runDir.resolve("lib").resolve("app.jar"), outsideEntry, runDir.resolve("lib").resolve("util.jar")),
      runDir,
    )

    assertThat(classPathString).isEqualTo("lib/app.jar\n${outsideEntry.invariantSeparatorsPathString}\nlib/util.jar")
  }

  @Test
  fun formatCoreClasspathOfEmptyClassPathIsEmpty() {
    assertThat(formatCoreClasspath(emptyList(), tempDir.resolve("run"))).isEmpty()
  }

  private fun createBuildRequest(
    classesOutputDirectory: Path? = null,
    scratchDir: Path? = null,
    buildDateInSeconds: Long? = null,
    linkImmutableCacheEntries: Boolean = true,
  ): BuildRequest {
    return BuildRequest(
      platformPrefix = "idea",
      additionalModules = emptyList(),
      projectDir = COMMUNITY_ROOT.communityRoot,
      classesOutputDirectory = classesOutputDirectory,
      scratchDir = scratchDir,
      buildDateInSeconds = buildDateInSeconds,
      linkImmutableCacheEntries = linkImmutableCacheEntries,
    )
  }

  private fun createProjectDevBuildOptions(request: BuildRequest, buildDir: Path, buildOptionsTemplate: BuildOptions): BuildOptions {
    @Suppress("UNCHECKED_CAST")
    return createProjectDevBuildOptionsMethod.invoke(null, request, buildDir, buildOptionsTemplate) as BuildOptions
  }

  private fun createConfiguration(homePath: Path) {
    createConfigurationMethod.invoke(null, homePath)
  }

  private fun restoreSystemProperty(name: String, value: String?) {
    if (value == null) {
      System.clearProperty(name)
    }
    else {
      System.setProperty(name, value)
    }
  }

  companion object {
    private val createProjectDevBuildOptionsMethod: Method = Class
      .forName("org.jetbrains.intellij.build.dev.IdeBuilderKt")
      .getDeclaredMethod("createProjectDevBuildOptions", BuildRequest::class.java, Path::class.java, BuildOptions::class.java)
      .also { it.isAccessible = true }

    private val createConfigurationMethod: Method = Class
      .forName("org.jetbrains.intellij.build.dev.BuildServerKt")
      .getDeclaredMethod("createConfiguration", Path::class.java)
      .also { it.isAccessible = true }
  }
}
