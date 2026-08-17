// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.DevBuildComponentEntry
import org.jetbrains.intellij.build.dev.DevBuildComponentManifest
import org.jetbrains.intellij.build.dev.DevBuildFragment
import org.jetbrains.intellij.build.dev.IdeFingerprintEntry
import org.jetbrains.intellij.build.dev.PlatformFragmentSelector
import org.jetbrains.intellij.build.dev.PluginFragmentSelector
import org.jetbrains.intellij.build.dev.PlatformJarOwnership
import org.jetbrains.intellij.build.dev.accepts
import org.jetbrains.intellij.build.dev.checkNamesAreKnown
import org.jetbrains.intellij.build.dev.computeIdeFingerprintFromComponents
import org.jetbrains.intellij.build.dev.configureDevModeBuildOptions
import org.jetbrains.intellij.build.dev.configureTargetPlatform
import org.jetbrains.intellij.build.dev.computeIdeFingerprint
import org.jetbrains.intellij.build.dev.copyWithDevBuildOverrides
import org.jetbrains.intellij.build.dev.createDevBuildPaths
import org.jetbrains.intellij.build.dev.formatCoreClasspath
import org.jetbrains.intellij.build.dev.prepareOverriddenRunDir
import org.jetbrains.intellij.build.dev.prepareScratchDir
import org.jetbrains.intellij.build.impl.ModuleIncludeReasons
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
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
  fun completeFragmentOwnsEverythingAndNeedsNoManifest() {
    val complete = DevBuildFragment.COMPLETE

    assertThat(complete.isComplete).isTrue()
    assertThat(complete.platform).isEqualTo(PlatformFragmentSelector.All)
    assertThat(complete.platformResources).isTrue()
    assertThat(complete.plugins).isEqualTo(PluginFragmentSelector.All)
    assertThat(
      DevBuildFragment(
        name = "platform_core",
        platform = PlatformFragmentSelector.Core,
        platformResources = false,
        plugins = null,
      ).isComplete
    ).isFalse()
  }

  @Test
  fun platformSelectorsPartitionLibJarsByContentModuleSet() {
    val layout = listOf(
      platformModule(),
      contentModule("intellij.libraries.asm", "libraries.platform"),
      contentModule("intellij.platform.lang.impl", "core.lang"),
      contentModule("intellij.charts", null),
    )
    val ownership = PlatformJarOwnership.of(layout)
    val jars = listOf(
      "app-backend.jar",
      // Named by no module: a project library, or one packing kept in its own jar. The layout never mentions it.
      "swingx.jar",
      "intellij.libraries.asm.jar",
      "intellij.platform.lang.impl.jar",
      "intellij.charts.jar",
    )

    val core = PlatformFragmentSelector.Core
    val libraries = PlatformFragmentSelector.ContentModuleSets(setOf("libraries.platform"))
    val remaining = PlatformFragmentSelector.RemainingContentModules(setOf("libraries.platform"))

    // Every jar belongs to exactly one of the three, so the fragments partition `lib` instead of overlapping or losing a jar.
    for (jar in jars) {
      val owners = listOf(core, libraries, remaining).filter { it.accepts(ownership, jar) }
      assertThat(owners).describedAs(jar).hasSize(1)
      assertThat(PlatformFragmentSelector.All.accepts(ownership, jar)).describedAs(jar).isTrue()
    }

    assertThat(core.accepts(ownership, "app-backend.jar")).isTrue()
    // A jar the layout does not name is the core fragment's, which is what keeps it out of no fragment at all.
    assertThat(core.accepts(ownership, "swingx.jar")).isTrue()
    assertThat(core.accepts(ownership, "")).isTrue()
    assertThat(libraries.accepts(ownership, "intellij.libraries.asm.jar")).isTrue()
    // A content module in no module set is still assembled - by the fragment that takes what nobody claimed.
    assertThat(remaining.accepts(ownership, "intellij.charts.jar")).isTrue()
    assertThat(remaining.accepts(ownership, "intellij.platform.lang.impl.jar")).isTrue()
  }

  @Test
  fun platformOwnershipRejectsAJarSharedByTwoModuleSets() {
    val modules = listOf(
      contentModule("intellij.platform.lang.impl", "core.lang").withOutputFile("shared.jar"),
      contentModule("intellij.libraries.asm", "libraries.platform").withOutputFile("shared.jar"),
    )

    assertThatThrownBy { PlatformJarOwnership.of(modules) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("holds content modules from two module sets")
  }

  @Test
  fun platformSelectorRejectsAModuleSetTheProductDoesNotDeclare() {
    val ownership = PlatformJarOwnership.of(listOf(contentModule("intellij.libraries.asm", "libraries.platform")))

    assertThatThrownBy {
      PlatformFragmentSelector.ContentModuleSets(setOf("libraries.platfrom")).checkNamesAreKnown(ownership, "platform_cm_typo")
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("libraries.platfrom")
    // A set the product does declare is fine even when this target platform gives it no jar.
    PlatformFragmentSelector.ContentModuleSets(setOf("libraries.platform")).checkNamesAreKnown(ownership, "platform_cm_libraries_platform")
  }

  @Test
  fun pluginSelectorRejectsAPluginTheProductDoesNotBundle() {
    assertThatThrownBy {
      PluginFragmentSelector.Named(setOf("intellij.air.plugn")).checkNamesAreKnown(setOf("intellij.air.plugin"), "plugins_air")
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("intellij.air.plugn")
  }

  @Test
  fun pluginSelectorsPartitionBundledPlugins() {
    val named = PluginFragmentSelector.Named(setOf("intellij.air.plugin"))
    val remaining = PluginFragmentSelector.Remaining(setOf("intellij.air.plugin"))

    assertThat(named.accepts("intellij.air.plugin")).isTrue()
    assertThat(named.accepts("intellij.vcs.git")).isFalse()
    assertThat(remaining.accepts("intellij.air.plugin")).isFalse()
    assertThat(remaining.accepts("intellij.vcs.git")).isTrue()
    assertThat(PluginFragmentSelector.All.accepts("intellij.air.plugin")).isTrue()
  }

  /** A module the platform merges into a shared jar, which is what makes that jar the core fragment's. */
  private fun platformModule(): ModuleItem {
    return ModuleItem(moduleName = "intellij.platform.ide.impl", relativeOutputFile = "app-backend.jar", reason = "addModule")
  }

  private fun contentModule(moduleName: String, setName: String?): ModuleItem {
    return ModuleItem(
      moduleName = moduleName,
      relativeOutputFile = "$moduleName.jar",
      reason = ModuleIncludeReasons.PRODUCT_MODULES,
      moduleSet = setName?.let { listOf("intellij.moduleSets.$it") },
    )
  }

  private fun ModuleItem.withOutputFile(relativeOutputFile: String): ModuleItem {
    return ModuleItem(moduleName = moduleName, relativeOutputFile = relativeOutputFile, reason = reason, moduleSet = moduleSet)
  }

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
  fun contentModuleFragmentDoesNotInlineTheProductDescriptor() {
    val options = BuildOptions()

    configureDevModeBuildOptions(
      options = options,
      request = createBuildRequest(
        fragment = DevBuildFragment(
          name = "platform_cm_libraries_platform",
          platform = PlatformFragmentSelector.ContentModuleSets(setOf("libraries.platform")),
          platformResources = false,
          plugins = null,
        ),
      ),
      buildOptionsTemplate = BuildOptions(),
    )

    assertThat(options.embedProductContentModuleDescriptors).isFalse()
  }

  @Test
  fun coreFragmentInlinesTheProductDescriptorBecauseItPacksTheJarThatCarriesIt() {
    val options = BuildOptions()

    configureDevModeBuildOptions(
      options = options,
      request = createBuildRequest(
        fragment = DevBuildFragment(
          name = "platform_core",
          platform = PlatformFragmentSelector.Core,
          platformResources = false,
          plugins = null,
        ),
      ),
      buildOptionsTemplate = BuildOptions(),
    )

    assertThat(options.embedProductContentModuleDescriptors).isTrue()
  }

  @Test
  fun theFragmentWritingTheClasspathPrefixInlinesTheProductDescriptorWhateverElseItOwns() {
    val options = BuildOptions()

    configureDevModeBuildOptions(
      options = options,
      request = createBuildRequest(
        fragment = DevBuildFragment(
          name = "platform_cm_rest",
          platform = PlatformFragmentSelector.RemainingContentModules(emptySet()),
          platformResources = false,
          plugins = null,
        ),
        pluginClasspathPrefixFile = tempDir.resolve("plugin-classpath-prefix"),
      ),
      buildOptionsTemplate = BuildOptions(),
    )

    assertThat(options.embedProductContentModuleDescriptors).isTrue()
  }

  @Test
  fun aCompleteDistributionInlinesTheProductDescriptor() {
    val options = BuildOptions()

    configureDevModeBuildOptions(options = options, request = createBuildRequest(), buildOptionsTemplate = BuildOptions())

    assertThat(options.embedProductContentModuleDescriptors).isTrue()
  }

  @Test
  fun targetPlatformAppliesOsAndArchitectureTogether() {
    val targetOs = if (OsFamily.currentOs == OsFamily.LINUX) OsFamily.MACOS else OsFamily.LINUX
    val targetArch = if (JvmArchitecture.currentJvmArch == JvmArchitecture.aarch64) JvmArchitecture.x64 else JvmArchitecture.aarch64
    val options = BuildOptions()

    configureTargetPlatform(options, createBuildRequest(os = targetOs, arch = targetArch))

    assertThat(options.targetOs).containsExactly(targetOs)
    assertThat(options.targetArch).isEqualTo(targetArch)
  }

  @Test
  fun targetPlatformReplacesInheritedTargetWithTheHostPlatform() {
    val inheritedOs = if (OsFamily.currentOs == OsFamily.LINUX) OsFamily.MACOS else OsFamily.LINUX
    val inheritedArch = if (JvmArchitecture.currentJvmArch == JvmArchitecture.aarch64) JvmArchitecture.x64 else JvmArchitecture.aarch64
    val options = BuildOptions().apply {
      targetOs = persistentListOf(inheritedOs)
      targetArch = inheritedArch
    }

    configureTargetPlatform(options, createBuildRequest())

    assertThat(options.targetOs).containsExactly(OsFamily.currentOs)
    assertThat(options.targetArch).isEqualTo(JvmArchitecture.currentJvmArch)
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
  fun buildProductClearsThrowawayScratchDataButKeepsTheLog() {
    val scratchDir = tempDir.resolve("scratch")
    val staleTempFile = Files.createDirectories(scratchDir.resolve("temp/native")).resolve("libsqliteij.jnilib")
    val staleArtifact = Files.createDirectories(scratchDir.resolve("artifacts")).resolve("dist.zip")
    val logFile = Files.createDirectories(scratchDir.resolve("log")).resolve("debug.log")
    Files.writeString(staleTempFile, "extracted by the previous build")
    Files.writeString(staleArtifact, "built by the previous build")
    Files.writeString(logFile, "the previous build")

    runBlocking { prepareScratchDir(scratchDir) }

    assertThat(scratchDir.resolve("temp")).doesNotExist()
    assertThat(scratchDir.resolve("artifacts")).doesNotExist()
    assertThat(logFile).exists()
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

  @Test
  fun ideFingerprintIncludesPathTypeAndContentButNotInputOrder() {
    val runDir = tempDir.resolve("run")
    val projectDir = tempDir.resolve("project")
    val first = CustomAssetEntry(path = runDir.resolve("lib/first.jar"), hash = 1)
    val second = CustomAssetEntry(path = runDir.resolve("plugins/sample/lib/second.jar"), hash = 2)

    val fingerprint = computeIdeFingerprint(sequenceOf(first, second), runDir, projectDir)

    assertThat(fingerprint).startsWith("v5:")
    assertThat(computeIdeFingerprint(sequenceOf(second, first), runDir, projectDir)).isEqualTo(fingerprint)
    assertThat(computeIdeFingerprint(sequenceOf(first.copy(hash = 3), second), runDir, projectDir)).isNotEqualTo(fingerprint)
    assertThat(
      computeIdeFingerprint(
        sequenceOf(first.copy(path = runDir.resolve("lib/renamed.jar"), distributionPath = runDir.resolve("lib/renamed.jar")), second),
        runDir,
        projectDir,
      )
    )
      .isNotEqualTo(fingerprint)
    assertThat(computeIdeFingerprint(sequenceOf(first.copy(relativeOutputFile = "lib/moved.jar"), second), runDir, projectDir))
      .isEqualTo(fingerprint)
  }

  @Test
  fun ideFingerprintNormalizesPathsAndIncludesEveryDuplicateContribution() {
    val runDir = tempDir.resolve("run")
    val projectDir = tempDir.resolve("project")
    val first = CustomAssetEntry(path = runDir.resolve("lib/shared.jar"), hash = 1)
    val second = CustomAssetEntry(
      path = runDir.resolve("ignored.jar"),
      hash = 2,
      distributionPath = runDir.resolve("lib/../lib/shared.jar"),
    )

    val fingerprint = computeIdeFingerprint(sequenceOf(first, second), runDir, projectDir)

    assertThat(computeIdeFingerprint(sequenceOf(second, first), runDir, projectDir)).isEqualTo(fingerprint)
    assertThat(computeIdeFingerprint(sequenceOf(first, second.copy(hash = 3)), runDir, projectDir)).isNotEqualTo(fingerprint)
    assertThat(computeIdeFingerprint(sequenceOf(first), runDir, projectDir)).isNotEqualTo(fingerprint)
    assertThat(computeIdeFingerprint(sequenceOf(second), runDir, projectDir))
      .isEqualTo(
        computeIdeFingerprint(
          sequenceOf(second.copy(distributionPath = runDir.resolve("lib/shared.jar"))),
          runDir,
          projectDir,
        )
      )
  }

  @Test
  fun ideFingerprintUsesDistributionPathForExternalCacheAsset() {
    val runDir = tempDir.resolve("run")
    val projectDir = tempDir.resolve("project")
    val distributionPath = runDir.resolve("plugins/rider-plugins-renderdoc")
    val entry = CustomAssetEntry(
      path = tempDir.resolve("maven/renderdoc-runtime-linux-aarch64.jar"),
      hash = 1,
      distributionPath = distributionPath,
    )

    val fingerprint = computeIdeFingerprint(sequenceOf(entry), runDir, projectDir)

    assertThat(computeIdeFingerprint(sequenceOf(entry.copy(path = tempDir.resolve("other-cache/renderdoc.jar"))), runDir, projectDir))
      .isEqualTo(fingerprint)
    assertThat(
      computeIdeFingerprint(
        sequenceOf(entry.copy(distributionPath = runDir.resolve("plugins/renamed-renderdoc"))),
        runDir,
        projectDir,
      )
    ).isNotEqualTo(fingerprint)
  }

  @Test
  fun ideFingerprintIncludesEntryTypeAndExecutableBitAndKeepsFieldsPrimitive() {
    val fingerprint = computeIdeFingerprint(listOf(IdeFingerprintEntry("lib/asset.jar", "custom-asset", 1)))

    assertThat(computeIdeFingerprint(listOf(IdeFingerprintEntry("lib/asset.jar", "module-output", 1)))).isNotEqualTo(fingerprint)
    assertThat(computeIdeFingerprint(listOf(IdeFingerprintEntry("lib/asset.jar", "custom-asset", 1, executable = true))))
      .isNotEqualTo(fingerprint)
    assertThat(IdeFingerprintEntry::class.java.getDeclaredField("hash").type).isEqualTo(java.lang.Long.TYPE)
    assertThat(IdeFingerprintEntry::class.java.getDeclaredField("executable").type).isEqualTo(java.lang.Boolean.TYPE)
  }

  @Test
  fun componentFingerprintIsStableAcrossComponentOrderAndIncludesEntryMode() {
    val platformEntry = DevBuildComponentEntry(relativePath = "lib/platform.jar", type = "module-output", hash = 1)
    val pluginEntry = DevBuildComponentEntry(relativePath = "plugins/sample/lib/plugin.jar", type = "module-output", hash = 2)
    val platform = componentManifest(kind = "platform", entries = listOf(platformEntry))
    val plugins = componentManifest(kind = "plugins", entries = listOf(pluginEntry))

    val fingerprint = computeIdeFingerprintFromComponents(listOf(platform, plugins))

    assertThat(computeIdeFingerprintFromComponents(listOf(plugins, platform))).isEqualTo(fingerprint)
    assertThat(
      computeIdeFingerprintFromComponents(
        listOf(platform.copy(entries = listOf(platformEntry.copy(executable = true))), plugins)
      )
    ).isNotEqualTo(fingerprint)
  }

  @Test
  fun ideFingerprintRejectsAnEntryOutsideKnownRoots() {
    val entry = CustomAssetEntry(path = tempDir.resolve("external/asset.zip"), hash = 1)

    assertThatThrownBy { computeIdeFingerprint(sequenceOf(entry), tempDir.resolve("run"), tempDir.resolve("project")) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("outside the distribution and project roots")
  }

  private fun createBuildRequest(
    classesOutputDirectory: Path? = null,
    scratchDir: Path? = null,
    buildDateInSeconds: Long? = null,
    os: OsFamily = OsFamily.currentOs,
    arch: JvmArchitecture = JvmArchitecture.currentJvmArch,
    fragment: DevBuildFragment = DevBuildFragment.COMPLETE,
    pluginClasspathPrefixFile: Path? = null,
  ): BuildRequest {
    return BuildRequest(
      platformPrefix = "idea",
      additionalModules = emptyList(),
      projectDir = COMMUNITY_ROOT.communityRoot,
      classesOutputDirectory = classesOutputDirectory,
      scratchDir = scratchDir,
      buildDateInSeconds = buildDateInSeconds,
      os = os,
      arch = arch,
      fragment = fragment,
      componentManifestFile = if (fragment.isComplete) null else tempDir.resolve("${fragment.name}.component.json"),
      pluginClasspathPrefixFile = pluginClasspathPrefixFile,
    )
  }

  private fun componentManifest(kind: String, entries: List<DevBuildComponentEntry>): DevBuildComponentManifest {
    return DevBuildComponentManifest(
      kind = kind,
      platformPrefix = "idea",
      os = OsFamily.currentOs.osId,
      arch = JvmArchitecture.currentJvmArch.name,
      additionalModules = emptyList(),
      mainClass = "com.intellij.idea.Main",
      coreClassPath = emptyList(),
      entries = entries,
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
