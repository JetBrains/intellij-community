// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.SystemProperties
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.api.GlobalOptions
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

class BuildOptions(
  @ApiStatus.Internal
  @JvmField
  val jarCacheDir: Path? = null,
  @ApiStatus.Internal
  @JvmField
  val compressZipFiles: Boolean = true,
  /** See [GlobalOptions.BUILD_DATE_IN_SECONDS]. */
  val buildDateInSeconds: Long = computeBuildDateInSeconds(),
  @ApiStatus.Internal
  @JvmField
  val printFreeSpace: Boolean = true,
  @ApiStatus.Internal
  @JvmField
  val validateImplicitPlatformModule: Boolean = true,
  @JvmField
  var skipDependencySetup: Boolean = false,
) {
  companion object {
    /**
     * Use this property to change the project's compiled classes output directory.
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
     * If this value is set no distributions of the product will be produced,
     * only [non-bundled plugins][ProductModulesLayout.pluginModulesToPublish] will be built.
     */
    const val OS_NONE = "none"

    /** Build actual searchableOptions.xml file. If skipped, the (possibly outdated) source version of the file will be used. */
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

    /** Notarize macOS distribution.  */
    const val MAC_NOTARIZE_STEP = "mac_notarize"

    /** Build Linux artifacts.  */
    const val LINUX_ARTIFACTS_STEP = "linux_artifacts"

    /** Build Linux tar.gz artifact without bundled Runtime.  */
    const val LINUX_TAR_GZ_WITHOUT_BUNDLED_RUNTIME_STEP = "linux_tar_gz_without_jre"

    /** Build *.exe installer for Windows distribution. If skipped, only .zip archive will be produced.  */
    const val WINDOWS_EXE_INSTALLER_STEP = "windows_exe_installer"

    /** Sign *.exe files in Windows distribution.  */
    const val WIN_SIGN_STEP = "windows_sign"

    @JvmField
    @ApiStatus.Internal
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

    /** Generate files containing lists of used third-party libraries  */
    const val THIRD_PARTY_LIBRARIES_LIST_STEP = "third_party_libraries"

    /** Build community distributives  */
    const val COMMUNITY_DIST_STEP = "community_dist"
    const val OS_SPECIFIC_DISTRIBUTIONS_STEP = "os_specific_distributions"
    const val PREBUILD_SHARED_INDEXES = "prebuild_shared_indexes"
    const val SETUP_BUNDLED_MAVEN = "setup_bundled_maven"
    const val VERIFY_CLASS_FILE_VERSIONS = "verify_class_file_versions"

    const val ARCHIVE_PLUGINS = "archivePlugins"

    const val VALIDATE_PLUGINS_TO_BE_PUBLISHED = "validatePluginsToBePublished"

    /**
     * Publish artifacts to TeamCity storage while the build is still running, immediately after the artifacts are built.
     * Comprises many small publication steps.
     * Note: skipping this step won't affect publication of 'Artifact paths' in TeamCity build settings and vice versa
     */
    const val TEAMCITY_ARTIFACTS_PUBLICATION_STEP = "teamcity_artifacts_publication"

    /**
     * @see org.jetbrains.intellij.build.fus.createStatisticsRecorderBundledMetadataProviderTask
     */
    const val FUS_METADATA_BUNDLE_STEP = "fus_metadata_bundle_step"

    /**
     * @see org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
     */
    const val REPAIR_UTILITY_BUNDLE_STEP = "repair_utility_bundle_step"

    const val DOC_AUTHORING_ASSETS_STEP = "doc_authoring_assets"

    /**
     * By default, a build cleans up output directory before compilation. Use this property to change the behavior.
     */
    const val CLEAN_OUTPUT_DIRECTORY_PROPERTY = "intellij.build.clean.output.root"

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
     * By default, if the incremental compilation fails, a clean rebuild is attempted.
     */
    const val INCREMENTAL_COMPILATION_FALLBACK_REBUILD_PROPERTY = "intellij.build.incremental.compilation.fallback.rebuild"

    /**
     * Enables module structure validation, `false` by default.
     */
    const val VALIDATE_MODULES_STRUCTURE_PROPERTY = "intellij.build.module.structure"

    /**
     * Max number of dependency resolution retry attempts. `1` means no retries.
     *
     * @see [org.jetbrains.intellij.build.impl.JpsCompilationRunner.resolveProjectDependencies]
     */
    const val RESOLVE_DEPENDENCIES_MAX_ATTEMPTS_PROPERTY = "intellij.build.dependencies.resolution.retry.max.attempts"

    /**
     * Initial delay in milliseconds between dependency resolution retries on fault. Default is `1000`.
     *
     * @see [org.jetbrains.intellij.build.impl.JpsCompilationRunner.resolveProjectDependencies]
     */
    const val RESOLVE_DEPENDENCIES_DELAY_MS_PROPERTY = "intellij.build.dependencies.resolution.retry.delay.ms"
    const val TARGET_OS_PROPERTY = "intellij.build.target.os"
    const val TARGET_ARCH_PROPERTY = "intellij.build.target.arch"

    /**
     * If `true`, the project modules will be compiled incrementally.
     */
    const val INTELLIJ_BUILD_INCREMENTAL_COMPILATION = "intellij.build.incremental.compilation"

    /**
     * Allows to override [ApplicationInfoProperties.isEAP].
     */
    const val INTELLIJ_BUILD_OVERRIDE_APPLICATION_VERSION_IS_EAP = "intellij.build.override.application.version.is.eap"

    /**
     * Allows to override [ApplicationInfoProperties.versionSuffix].
     */
    const val INTELLIJ_BUILD_OVERRIDE_APPLICATION_VERSION_SUFFIX = "intellij.build.override.application.version.suffix"

    /**
     * Allows to override [ApplicationInfoProperties.majorReleaseDate].
     */
    const val INTELLIJ_BUILD_OVERRIDE_APPLICATION_VERSION_MAJOR_RELEASE_DATE = "intellij.build.override.application.version.majorReleaseDate"

    /**
     * Pass comma-separated names of build steps (see below) to this system property to skip them.
     */
    const val BUILD_STEPS_TO_SKIP_PROPERTY = "intellij.build.skip.build.steps"

    /**
     * By default, the build process produces temporary and resulting files under `<projectHome>/out/<productName>` directory.
     * Use this property to change the output directory.
     */
    const val INTELLIJ_BUILD_OUTPUT_ROOT = "intellij.build.output.root"

    /**
     * Path to a zip file containing 'production' and 'test' directories with compiled classes of the project modules inside.
     */
    const val INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE = "intellij.build.compiled.classes.archive"

    /**
     * By default, calculated based on build number.
     */
    const val INTELLIJ_BUILD_IS_NIGHTLY = "intellij.build.is.nightly"

    /**
     * IJPL-176 Download pre-compiled IJent executables.
     */
    const val IJENT_EXECUTABLE_DOWNLOADING = "ijent.executable.downloading"

    @Suppress("SpellCheckingInspection")
    private const val DEFAULT_SNAP_TOOL_IMAGE = "snapcore/snapcraft:stable@sha256:6d771575c134569e28a590f173f7efae8bf7f4d1746ad8a474c98e02f4a3f627"
  }

  var classesOutputDirectory: String? = System.getProperty(PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY)

  /**
   * Specifies for which operating systems distributions should be built.
   */
  var targetOs: PersistentList<OsFamily>

  /**
   * Specifies for which arch distributions should be built. `null` means all.
   */
  var targetArch: JvmArchitecture? = null

  fun setTargetOsAndArchToCurrent() {
    targetOs = persistentListOf(OsFamily.currentOs)
    targetArch = JvmArchitecture.currentJvmArch
  }

  /**
   * If `true`, the build is running in the 'Development mode', i.e., its artifacts aren't supposed to be used in production.
   * In the development mode, build scripts won't fail if some non-mandatory dependencies are missing and will just show warnings.
   *
   * By default, the development mode is enabled if the build is not running on a continuous integration server (TeamCity).
   */
  var isInDevelopmentMode = SystemProperties.getBooleanProperty("intellij.build.dev.mode", System.getenv("TEAMCITY_VERSION") == null)
  var useCompiledClassesFromProjectOutput = SystemProperties.getBooleanProperty(USE_COMPILED_CLASSES_PROPERTY, isInDevelopmentMode)

  /**
   * Pass comma-separated names of build steps (see below) to [BUILD_STEPS_TO_SKIP_PROPERTY] system property to skip them when building locally.
   */
  var buildStepsToSkip: MutableSet<String> = System.getProperty(BUILD_STEPS_TO_SKIP_PROPERTY, "")
    .split(',')
    .dropLastWhile { it.isEmpty() }
    .filterNot { it.isBlank() }
    .toMutableSet()
    .apply {
      /* Skip signing and notarization for local builds */
      if (isInDevelopmentMode) {
        add(MAC_SIGN_STEP)
        add(MAC_NOTARIZE_STEP)
      }
    }

  /**
   * Pass `true` to this system property to produce .snap packages.
   * A build configuration should have "docker.version >= 17" in requirements.
   */
  var buildUnixSnaps = SystemProperties.getBooleanProperty("intellij.build.unix.snaps", false)

  /**
   * Docker image for snap package creation
   */
  var snapDockerImage: String = System.getProperty("intellij.build.snap.docker.image", DEFAULT_SNAP_TOOL_IMAGE)
  var snapDockerBuildTimeoutMin: Long = System.getProperty("intellij.build.snap.timeoutMin", "20").toLong()

  /**
   * Path to a zip file containing 'production' and 'test' directories with compiled classes of the project modules inside.
   */
  var pathToCompiledClassesArchive: Path? = System.getProperty(INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE)?.let { Path.of(it) }

  /**
   * Path to a metadata file containing urls with compiled classes of the project modules inside.
   * Metadata is a [org.jetbrains.intellij.build.impl.compilation.CompilationPartsMetadata] serialized into JSON format.
   */
  var pathToCompiledClassesArchivesMetadata: String? = System.getProperty("intellij.build.compiled.classes.archives.metadata")

  /**
   * If `true`, the project modules will be compiled incrementally.
   */
  var incrementalCompilation: Boolean = SystemProperties.getBooleanProperty(INTELLIJ_BUILD_INCREMENTAL_COMPILATION, false)

  /**
   * If `true`, and the incremental compilation fails, fallback to downloading Portable Compilation Cache and full rebuild.
   */
  var incrementalCompilationFallbackRebuild: Boolean = SystemProperties.getBooleanProperty(INCREMENTAL_COMPILATION_FALLBACK_REBUILD_PROPERTY, true)

  /**
   * Full rebuild will be triggered if this timeout is exceeded for incremental compilation.
   */
  val incrementalCompilationTimeout: Long = SystemProperties.getLongProperty("intellij.build.incremental.compilation.timeoutMin", Long.MAX_VALUE)

  /**
   * Build number without product code (e.g. '162.500.10'); if `null`, `<baseline>.SNAPSHOT` will be used.
   * Use [BuildContext.buildNumber] to get the actual build number in build scripts.
   */
  var buildNumber: String? = System.getProperty("build.number")

  /**
   * By default, the build process produces temporary and resulting files under `<projectHome>/out/<productName>` directory.
   * Use this property to change the output directory.
   */
  var outputRootPath: Path? = System.getProperty(INTELLIJ_BUILD_OUTPUT_ROOT)?.let { Path.of(it).toAbsolutePath().normalize() }

  var logPath: String? = System.getProperty("intellij.build.log.root")

  /**
   * If `true`, write all compilation messages into a separate file (`compilation.log`).
   */
  var compilationLogEnabled: Boolean = SystemProperties.getBooleanProperty("intellij.build.compilation.log.enabled", true)

  var cleanOutputFolder: Boolean = SystemProperties.getBooleanProperty(CLEAN_OUTPUT_DIRECTORY_PROPERTY, true)

  /**
   * If `true`, the build is running as a unit test.
   */
  var isTestBuild: Boolean = SystemProperties.getBooleanProperty("intellij.build.test.mode", false)

  /**
   * If 'true', print system properties and environment variables to stdout. Useful for build scripts debugging.
   */
  var printEnvironmentInfo: Boolean = SystemProperties.getBooleanProperty("intellij.print.environment", false)

  /**
   * Specifies a list of directory names for bundled plugins that should not be included in the product distribution.
   * This option can be used to speed up updating the IDE from sources.
   */
  val bundledPluginDirectoriesToSkip: Set<String> = getSetProperty("intellij.build.bundled.plugin.dirs.to.skip")

  /**
   * Specifies a list of directory names for non-bundled plugins (determined by [ProductModulesLayout.pluginModulesToPublish] and
   * [ProductModulesLayout.buildAllCompatiblePlugins]) which should be actually built. This option can be used to speed up updating
   * the IDE from sources. By default, all plugins determined by [ProductModulesLayout.pluginModulesToPublish] and
   * [ProductModulesLayout.buildAllCompatiblePlugins] are built.
   * To skip building all non-bundled plugins, set the property to `none`.
   */
  val nonBundledPluginDirectoriesToInclude: Set<String> = getSetProperty("intellij.build.non.bundled.plugin.dirs.to.include")

  /**
   * If this option and [ProductProperties.supportModularLoading] are set to `true`, a file containing module descriptors will be added to
   * the distribution (IJPL-109), and launchers will use it to start the IDE (IJPL-128).
   */
  @ApiStatus.Experimental
  var useModularLoader: Boolean = SystemProperties.getBooleanProperty("intellij.build.use.modular.loader", true)

  /**
   * If this option is set to `true` and [enableEmbeddedJetBrainsClient] is enabled,
   * a [runtime module repository][com.intellij.platform.runtime.repository.RuntimeModuleRepository] will be generated in the distribution.
   * This option doesn't make sense if [modular loader][BuildContext.useModularLoader] is used
   * (in this case, the generation is enabled automatically).
   */
  @ApiStatus.Experimental
  var generateRuntimeModuleRepository: Boolean = SystemProperties.getBooleanProperty("intellij.build.generate.runtime.module.repository", true)

  /**
   * If `true` and [ProductProperties.embeddedJetBrainsClientMainModule] is not null, the JAR files in the distribution will be adjusted
   * to allow starting JetBrains Client directly from the IDE's distribution.
   */
  @ApiStatus.Experimental
  var enableEmbeddedJetBrainsClient: Boolean = SystemProperties.getBooleanProperty("intellij.build.enable.embedded.jetbrains.client", true)

  /**
   * If `true` and embedded JetBrains Client is [enabled][BuildContext.isEmbeddedJetBrainsClientEnabled], launchers which start it will be
   * included in the IDE's distributions.
   */
  @ApiStatus.Experimental
  var includeLaunchersForEmbeddedJetBrainsClient = SystemProperties.getBooleanProperty("intellij.build.include.launchers.for.embedded.jetbrains.client", true) 

  /**
   * Specifies a prefix to use when looking for an artifact of a [org.jetbrains.intellij.build.JetBrainsRuntimeDistribution] to be bundled with distributions.
   * If `null`, `"jbr_jcef-"` will be used.
   */
  var bundledRuntimePrefix: String? = System.getProperty("intellij.build.bundled.jre.prefix")

  /**
   * Enables "fastdebug" runtime.
   */
  var runtimeDebug: Boolean = parseBooleanValue(System.getProperty("intellij.build.bundled.jre.debug", "false"))

  var validateModuleStructure: Boolean = parseBooleanValue(System.getProperty(VALIDATE_MODULES_STRUCTURE_PROPERTY, "false"))

  @ApiStatus.Internal
  var skipCustomResourceGenerators: Boolean = false

  var resolveDependenciesMaxAttempts: Int = System.getProperty(RESOLVE_DEPENDENCIES_MAX_ATTEMPTS_PROPERTY, "2").toInt()
  var resolveDependenciesDelayMs: Long = System.getProperty(RESOLVE_DEPENDENCIES_DELAY_MS_PROPERTY, "1000").toLong()

  var randomSeedNumber: Long = 0

  var isNightlyBuild: Boolean = SystemProperties.getBooleanProperty(INTELLIJ_BUILD_IS_NIGHTLY, (buildNumber?.count { it == '.' } ?: 1) <= 1)

  /**
   * If `false`, [org.jetbrains.intellij.build.impl.projectStructureMapping.buildJarContentReport]
   * won't be affected by [PluginBundlingRestrictions.includeInDistribution]
   */
  @set:TestOnly
  @ApiStatus.Internal
  var useReleaseCycleRelatedBundlingRestrictionsForContentReport: Boolean = true

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
    targetArch = System.getProperty(TARGET_ARCH_PROPERTY)
      ?.takeIf { it.isNotBlank() }
      ?.let(JvmArchitecture::valueOf)
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
    text.equals(false.toString(), ignoreCase = true) -> false
    else -> throw IllegalArgumentException("Could not parse as boolean, accepted values are only 'true' or 'false': $text")
  }
}

private fun getSetProperty(name: String): Set<String> = System.getProperty(name)?.split(',')?.toSet() ?: emptySet()

private fun computeBuildDateInSeconds(): Long {
  val sourceDateEpoch = System.getenv(GlobalOptions.BUILD_DATE_IN_SECONDS)
  val minZipTime = GregorianCalendar(1980, 0, 1)
  val minZipTimeInSeconds = TimeUnit.MILLISECONDS.toSeconds(minZipTime.timeInMillis)
  val value = sourceDateEpoch?.toLong() ?: (System.currentTimeMillis() / 1000)
  require(value >= minZipTimeInSeconds) {
    ".zip archive cannot store timestamps older than ${minZipTime.time} " +
    "(see specification: https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) " +
    "but ${GlobalOptions.BUILD_DATE_IN_SECONDS}=$sourceDateEpoch was supplied. " +
    "If timestamps aren't stored then .zip content files modification time will be set to extraction time " +
    "diverging from modification times specified in .manifest."
  }
  return value
}
