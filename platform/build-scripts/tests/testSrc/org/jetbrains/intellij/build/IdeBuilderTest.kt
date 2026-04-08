// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.dev.BuildRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path

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

  private fun createBuildRequest(classesOutputDirectory: Path? = null): BuildRequest {
    return BuildRequest(
      platformPrefix = "idea",
      additionalModules = emptyList(),
      projectDir = COMMUNITY_ROOT.communityRoot,
      classesOutputDirectory = classesOutputDirectory,
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
