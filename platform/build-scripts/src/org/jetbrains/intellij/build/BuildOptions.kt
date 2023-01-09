// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.SystemProperties
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jps.api.GlobalOptions
import java.nio.file.Path
import java.util.concurrent.ThreadLocalRandom

/**
 * Pass comma-separated names of build steps (see below) to this system property to skip them.
 */
private const val BUILD_STEPS_TO_SKIP_PROPERTY = "intellij.build.skip.build.steps"

class BuildOptions {
  companion object {
    /**
     * Use this property to change the project compiled classes output directory.
     *
     * @see [org.jetbrains.intellij.build.impl.CompilationContextImpl.classesOutputDirectory]
     */
    const val PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY = "intellij.project.classes.output.directory"
    const val OS_LINUX = "linux"
    const val OS_WINDOWS = "windows"
    const val OS_MAC = "mac"
    const val OS_ALL = "all"
    const val OS_CURRENT = "current"

    /**
     * If this value is set no distributions of the product will be produced, only [non-bundled plugins][ProductModulesLayout.setPluginModulesToPublish]
     * will be built.
     */
    const val OS_NONE = "none"

    /** Pre-builds SVG icons for all SVG resource files to speedup icons loading at runtime  */
    const val SVGICONS_PREBUILD_STEP = "svg_icons_prebuild"

    /** Build actual searchableOptions.xml file. If skipped; the (possibly outdated) source version of the file will be used.  */
    const val SEARCHABLE_OPTIONS_INDEX_STEP = "search_index"
    const val BROKEN_PLUGINS_LIST_STEP = "broken_plugins_list"
    const val PROVIDED_MODULES_LIST_STEP = "provided_modules_list"
    const val GENERATE_JAR_ORDER_STEP = "jar_order"
    const val SOURCES_ARCHIVE_STEP = "sources_archive"
    const val SCRAMBLING_STEP = "scramble"
    const val NON_BUNDLED_PLUGINS_STEP = "non_bundled_plugins"

    /** Build Maven artifacts for IDE modules.  */
    const val MAVEN_ARTIFACTS_STEP = "maven_artifacts"

    /** Build macOS artifacts.  */
    const val MAC_ARTIFACTS_STEP = "mac_artifacts"

    /** Build .dmg file for macOS. If skipped, only .sit archive will be produced.  */
    const val MAC_DMG_STEP = "mac_dmg"

    /**
     * Publish .sit file for macOS. If skipped, only .dmg archive will be produced.
     * If skipped together with [MAC_DMG_STEP], only .zip archive will be produced.
     *
     * Note: .sit is required to build patches.
     */
    const val MAC_SIT_PUBLICATION_STEP = "mac_sit"

    /** Sign macOS distribution.  */
    const val MAC_SIGN_STEP = "mac_sign"

    /** Build Linux artifacts.  */
    const val LINUX_ARTIFACTS_STEP = "linux_artifacts"

    /** Build Linux tar.gz artifact without bundled Runtime.  */
    const val LINUX_TAR_GZ_WITHOUT_BUNDLED_RUNTIME_STEP = "linux_tar_gz_without_jre"

    /** Build *.exe installer for Windows distribution. If skipped, only .zip archive will be produced.  */
    const val WINDOWS_EXE_INSTALLER_STEP = "windows_exe_installer"

    /** Sign *.exe files in Windows distribution.  */
    const val WIN_SIGN_STEP = "windows_sign"

    @JvmField
    @Internal
    val WIN_SIGN_OPTIONS: PersistentMap<String, String> = System.getProperty("intellij.build.win.sign.options", "")
      .splitToSequence(';')
      .filter { !it.isBlank() }
      .associate {
        val item = it.split('=', limit = 2)
        require(item.size == 2) { "Could not split by '=': $it" }
        item[0] to item[1]
      }
      .toPersistentMap()

    /** Build Frankenstein artifacts.  */
    const val CROSS_PLATFORM_DISTRIBUTION_STEP = "cross_platform_dist"

    /** Toolbox links generator step  */
    const val TOOLBOX_LITE_GEN_STEP = "toolbox_lite_gen"

    /** Generate files containing lists of used third-party libraries  */
    const val THIRD_PARTY_LIBRARIES_LIST_STEP = "third_party_libraries"

    /** Build community distributives  */
    const val COMMUNITY_DIST_STEP = "community_dist"
    const val OS_SPECIFIC_DISTRIBUTIONS_STEP = "os_specific_distributions"
    const val PREBUILD_SHARED_INDEXES = "prebuild_shared_indexes"
    const val SETUP_BUNDLED_MAVEN = "setup_bundled_maven"
    const val VERIFY_CLASS_FILE_VERSIONS = "verify_class_file_versions"

    const val ARCHIVE_PLUGINS = "archivePlugins"

    /**
     * Publish artifacts to TeamCity storage while the build is still running, immediately after the artifacts are built.
     * Comprises many small publication steps.
     * Note: skipping this step won't affect publication of 'Artifact paths' in TeamCity build settings and vice versa
     */
    const val TEAMCITY_ARTIFACTS_PUBLICATION_STEP = "teamcity_artifacts_publication"

    /**
     * @see org.jetbrains.intellij.build.fus.StatisticsRecorderBundledMetadataProvider
     */
    const val FUS_METADATA_BUNDLE_STEP = "fus_metadata_bundle_step"

    /**
     * @see org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
     */
    const val REPAIR_UTILITY_BUNDLE_STEP = "repair_utility_bundle_step"

    /**
     * Pass 'true' to this system property to produce an additional .dmg and .sit archives for macOS without Runtime.
     */
    const val BUILD_MAC_ARTIFACTS_WITHOUT_RUNTIME = "intellij.build.dmg.without.bundled.jre"

    /**
     * Pass 'false' to this system property to skip building .dmg and .sit with bundled Runtime.
     */
    const val BUILD_MAC_ARTIFACTS_WITH_RUNTIME = "intellij.build.dmg.with.bundled.jre"

    /**
     * By default, build cleanup output folder before compilation, use this property to change this behaviour.
     */
    const val CLEAN_OUTPUT_FOLDER_PROPERTY = "intellij.build.clean.output.root"

    /**
     * If `false` build scripts compile project classes to a special output directory (to not interfere with the default project output if
     * invoked on a developer machine).
     * If `true` compilation step is skipped and compiled classes from the project output are used instead.
     * True if [BuildOptions.isInDevelopmentMode] is enabled.
     *
     * @see [org.jetbrains.intellij.build.impl.CompilationContextImpl.classesOutputDirectory]
     */
    const val USE_COMPILED_CLASSES_PROPERTY = "intellij.build.use.compiled.classes"

    /**
     * Enables module structure validation, false by default
     */
    const val VALIDATE_MODULES_STRUCTURE_PROPERTY = "intellij.build.module.structure"

    /**
     * Verify whether class files have a forbidden subpaths in them, false by default
     */
    const val VALIDATE_CLASSFILE_SUBPATHS_PROPERTY = "intellij.verify.classfile.subpaths"

    /**
     * Max attempts of dependencies resolution on fault. "1" means no retries.
     *
     * @see {@link org.jetbrains.intellij.build.impl.JpsCompilationRunner.resolveProjectDependencies}
     */
    const val RESOLVE_DEPENDENCIES_MAX_ATTEMPTS_PROPERTY = "intellij.build.dependencies.resolution.retry.max.attempts"

    /**
     * Initial delay in milliseconds between dependencies resolution retries on fault. Default is 1000
     *
     * @see {@link org.jetbrains.intellij.build.impl.JpsCompilationRunner.resolveProjectDependencies}
     */
    const val RESOLVE_DEPENDENCIES_DELAY_MS_PROPERTY = "intellij.build.dependencies.resolution.retry.delay.ms"
    const val TARGET_OS_PROPERTY = "intellij.build.target.os"
  }

  var classesOutputDirectory: String? = System.getProperty(PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY)

  /**
   * Specifies for which operating systems distributions should be built.
   */
  var targetOs: PersistentList<OsFamily>

  /**
   * Specifies for which arch distributions should be built. null means all
   */
  var targetArch: JvmArchitecture? = null

  /**
   * Pass comma-separated names of build steps (see below) to [BUILD_STEPS_TO_SKIP_PROPERTY] system property to skip them when building locally.
   */
  var buildStepsToSkip: MutableSet<String> = System.getProperty(BUILD_STEPS_TO_SKIP_PROPERTY, "")
    .split(',')
    .dropLastWhile { it.isEmpty() }
    .filterTo(HashSet()) { !it.isBlank() }

  var buildMacArtifactsWithoutRuntime = SystemProperties.getBooleanProperty(BUILD_MAC_ARTIFACTS_WITHOUT_RUNTIME,
                                                                            SystemProperties.getBooleanProperty("artifact.mac.no.jdk", false))
  var buildMacArtifactsWithRuntime = SystemProperties.getBooleanProperty(BUILD_MAC_ARTIFACTS_WITH_RUNTIME, true)

  /**
   * Pass 'true' to this system property to produce .snap packages.
   * A build configuration should have "docker.version >= 17" in requirements.
   */
  var buildUnixSnaps = SystemProperties.getBooleanProperty("intellij.build.unix.snaps", false)

  /**
   * Docker image for snap package creation
   */
  var snapDockerImage: String = System.getProperty("intellij.build.snap.docker.image", "snapcore/snapcraft:stable@sha256:6d771575c134569e28a590f173f7efae8bf7f4d1746ad8a474c98e02f4a3f627")
  var snapDockerBuildTimeoutMin: Long = System.getProperty("intellij.build.snap.timeoutMin", "20").toLong()

  /**
   * Path to a zip file containing 'production' and 'test' directories with compiled classes of the project modules inside.
   */
  var pathToCompiledClassesArchive: Path? = System.getProperty("intellij.build.compiled.classes.archive")?.let { Path.of(it) }

  /**
   * Path to a metadata file containing urls with compiled classes of the project modules inside.
   * Metadata is a [org.jetbrains.intellij.build.impl.compilation.CompilationPartsMetadata] serialized into json format
   */
  var pathToCompiledClassesArchivesMetadata: String? = System.getProperty("intellij.build.compiled.classes.archives.metadata")

  /**
   * If `true` the project modules will be compiled incrementally
   */
  var incrementalCompilation = SystemProperties.getBooleanProperty("intellij.build.incremental.compilation", false)

  /**
   * Build number without product code (e.g. '162.500.10'), if `null` '&lt;baseline&gt;.SNAPSHOT' will be used. Use [BuildContext.buildNumber] to
   * get the actual build number in build scripts.
   */
  var buildNumber: String? = System.getProperty("build.number")

  /**
   * By default, build process produces temporary and resulting files under projectHome/out/productName directory, use this property to
   * change the output directory.
   */
  var outputRootPath: Path? = System.getProperty("intellij.build.output.root")?.let { Path.of(it).toAbsolutePath().normalize() }

  var logPath: String? = System.getProperty("intellij.build.log.root")

  /**
   * If `true` write a separate compilation.log for all compilation messages
   */
  var compilationLogEnabled = SystemProperties.getBooleanProperty("intellij.build.compilation.log.enabled", true)
  var cleanOutputFolder = SystemProperties.getBooleanProperty(CLEAN_OUTPUT_FOLDER_PROPERTY, true)

  /**
   * If `true` the build is running in 'Development mode' i.e. its artifacts aren't supposed to be used in production. In development
   * mode build scripts won't fail if some non-mandatory dependencies are missing and will just show warnings.
   *
   * By default, 'development mode' is enabled if build is not running under continuous integration server (TeamCity).
   */
  var isInDevelopmentMode = SystemProperties.getBooleanProperty("intellij.build.dev.mode", System.getenv("TEAMCITY_VERSION") == null)
  var useCompiledClassesFromProjectOutput = SystemProperties.getBooleanProperty(USE_COMPILED_CLASSES_PROPERTY, isInDevelopmentMode)

  /**
   * If `true` the build is running as a unit test
   */
  var isTestBuild = SystemProperties.getBooleanProperty("intellij.build.test.mode", false)
  var skipDependencySetup = false

  /**
   * If 'true' print system properties and environment variables to stdout.
   * Mostly useful for build scripts debugging.
   */
  var printEnvironmentInfo = SystemProperties.getBooleanProperty("intellij.print.environment", false)

  @Internal
  var printFreeSpace = true

  /**
   * Specifies list of names of directories of bundled plugins which shouldn't be included into the product distribution. This option can be
   * used to speed up updating the IDE from sources.
   */
  val bundledPluginDirectoriesToSkip: Set<String> = getSetProperty("intellij.build.bundled.plugin.dirs.to.skip")

  /**
   * Specifies list of names of directories of non-bundled plugins (determined by [ProductModulesLayout.pluginsToPublish] and
   * [ProductModulesLayout.buildAllCompatiblePlugins]) which should be actually built. This option can be used to speed up updating
   * the IDE from sources. By default, all plugins determined by [ProductModulesLayout.pluginsToPublish] and
   * [ProductModulesLayout.buildAllCompatiblePlugins] are built. In order to skip building all non-bundled plugins, set the property to
   * `none`.
   */
  val nonBundledPluginDirectoriesToInclude = getSetProperty("intellij.build.non.bundled.plugin.dirs.to.include")

  /**
   * Specifies [org.jetbrains.intellij.build.JetBrainsRuntimeDistribution] build to be bundled with distributions. If `null` then `runtimeBuild` from [org.jetbrains.intellij.build.dependencies.DependenciesProperties] will be used.
   */
  var bundledRuntimeBuild: String? = System.getProperty("intellij.build.bundled.jre.build")

  /**
   * Specifies a prefix to use when looking for an artifact of a [org.jetbrains.intellij.build.JetBrainsRuntimeDistribution] to be bundled with distributions.
   * If `null`, `"jbr_jcef-"` will be used.
   */
  var bundledRuntimePrefix: String? = System.getProperty("intellij.build.bundled.jre.prefix")

  /**
   * Enables fastdebug runtime
   */
  var runtimeDebug = parseBooleanValue(System.getProperty("intellij.build.bundled.jre.debug", "false"))

  /**
   * Specifies an algorithm to build distribution checksums.
   */
  val hashAlgorithm = "SHA-384"

  var validateModuleStructure = parseBooleanValue(System.getProperty(VALIDATE_MODULES_STRUCTURE_PROPERTY, "false"))

  var validateClassFileSubpaths = parseBooleanValue(System.getProperty(VALIDATE_CLASSFILE_SUBPATHS_PROPERTY, "false"))

  @Internal
  var skipCustomResourceGenerators = false

  var resolveDependenciesMaxAttempts = System.getProperty(RESOLVE_DEPENDENCIES_MAX_ATTEMPTS_PROPERTY, "2").toInt()
  var resolveDependenciesDelayMs = System.getProperty(RESOLVE_DEPENDENCIES_DELAY_MS_PROPERTY, "1000").toLong()

  /**
   * See [GlobalOptions.BUILD_DATE_IN_SECONDS]
   */
  var buildDateInSeconds: Long = 0
  var randomSeedNumber: Long = 0

  @ApiStatus.Experimental
  @ApiStatus.Internal
  var compressZipFiles = true

  init {
    val targetOsId = System.getProperty(TARGET_OS_PROPERTY, OS_ALL).lowercase()
    targetOs = when {
      targetOsId == OS_CURRENT -> persistentListOf(OsFamily.currentOs)
      targetOsId.isEmpty() || targetOsId == OS_ALL -> OsFamily.ALL
      targetOsId == OS_NONE -> persistentListOf()
      targetOsId == OsFamily.MACOS.osId -> persistentListOf(OsFamily.MACOS)
      targetOsId == OsFamily.WINDOWS.osId -> persistentListOf(OsFamily.WINDOWS)
      targetOsId == OsFamily.LINUX.osId -> persistentListOf(OsFamily.LINUX)
      else -> throw IllegalStateException("Unknown target OS $targetOsId")
    }

    val sourceDateEpoch = System.getenv(GlobalOptions.BUILD_DATE_IN_SECONDS)
    buildDateInSeconds = sourceDateEpoch?.toLong() ?: (System.currentTimeMillis() / 1000)
    val randomSeedString = System.getProperty("intellij.build.randomSeed")
    randomSeedNumber = if (randomSeedString == null || randomSeedString.isBlank()) {
      ThreadLocalRandom.current().nextLong()
    }
    else {
      randomSeedString.toLong()
    }
  }
}

private fun parseBooleanValue(text: String): Boolean {
  return when {
    text.toBoolean() -> true
    text.equals(java.lang.Boolean.FALSE.toString(), ignoreCase = true) -> false
    else -> throw IllegalArgumentException("Could not parse as boolean, accepted values are only 'true' or 'false': $text")
  }
}

private fun getSetProperty(name: String): Set<String> {
  return java.util.Set.copyOf((System.getProperty(name) ?: return emptySet()).split(','))
}
