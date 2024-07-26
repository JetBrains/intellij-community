// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.SystemProperties
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import org.jetbrains.jps.api.GlobalOptions
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

data class BuildOptions(
  @ApiStatus.Internal @JvmField val jarCacheDir: Path? = null,
  @ApiStatus.Internal @JvmField val compressZipFiles: Boolean = true,
  /** See [GlobalOptions.BUILD_DATE_IN_SECONDS]. */
  val buildDateInSeconds: Long = computeBuildDateInSeconds(),
  @ApiStatus.Internal @JvmField val printFreeSpace: Boolean = true,
  @ApiStatus.Internal @JvmField val validateImplicitPlatformModule: Boolean = true,
  @JvmField var skipDependencySetup: Boolean = false,

  /**
   * If `true`, the build is running in the 'Development mode', i.e., its artifacts aren't supposed to be used in production.
   * In the development mode, build scripts won't fail if some non-mandatory dependencies are missing and will just show warnings.
   *
   * By default, the development mode is enabled if the build is not running on a continuous integration server (TeamCity).
   */
  var isInDevelopmentMode: Boolean = SystemProperties.getBooleanProperty("intellij.build.dev.mode", System.getenv("TEAMCITY_VERSION") == null),
  var useCompiledClassesFromProjectOutput: Boolean = SystemProperties.getBooleanProperty(USE_COMPILED_CLASSES_PROPERTY, isInDevelopmentMode),

  val cleanOutDir: Boolean = SystemProperties.getBooleanProperty(CLEAN_OUTPUT_DIRECTORY_PROPERTY, true),

  var classOutDir: String? = System.getProperty(PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY),

  var forceRebuild: Boolean = SystemProperties.getBooleanProperty(FORCE_REBUILD_PROPERTY, false),
  /**
   * If `true` and [ProductProperties.embeddedJetBrainsClientMainModule] is not null, the JAR files in the distribution will be adjusted
   * to allow starting JetBrains Client directly from the IDE's distribution.
   */
  @ApiStatus.Experimental
  var enableEmbeddedJetBrainsClient: Boolean = SystemProperties.getBooleanProperty("intellij.build.enable.embedded.jetbrains.client", true),

  /**
   * By default, the build process produces temporary and resulting files under `<projectHome>/out/<productName>` directory.
   * Use this property to change the output directory.
   */
  var outRootDir: Path? = System.getProperty(INTELLIJ_BUILD_OUTPUT_ROOT)?.let { Path.of(it).toAbsolutePath().normalize() },

  /**
   * Pass comma-separated names of build steps (see below) to [BUILD_STEPS_TO_SKIP_PROPERTY] system property to skip them when building locally.
   */
  var buildStepsToSkip: Set<String> = System.getProperty(BUILD_STEPS_TO_SKIP_PROPERTY, "")
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
    },
  /**
   * If `true`, write all compilation messages into a separate file (`compilation.log`).
   */
  @JvmField var compilationLogEnabled: Boolean = SystemProperties.getBooleanProperty("intellij.build.compilation.log.enabled", true),
  @JvmField val logDir: Path? = System.getProperty("intellij.build.log.root")?.let { Path.of(it) },

  /**
   * Path to a zip file containing 'production' and 'test' directories with compiled classes of the project modules inside.
   */
  @JvmField val pathToCompiledClassesArchive: Path? = System.getProperty(INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE)?.let { Path.of(it) },

  /**
   * Path to a metadata file containing urls with compiled classes of the project modules inside.
   * Metadata is a [org.jetbrains.intellij.build.impl.compilation.CompilationPartsMetadata] serialized into JSON format.
   */
  @JvmField val pathToCompiledClassesArchivesMetadata: String? = System.getProperty(INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA),

  /**
   * If `true` won't unpack downloaded jars with compiled classes from [pathToCompiledClassesArchivesMetadata].
   */
  @JvmField val unpackCompiledClassesArchives: Boolean = SystemProperties.getBooleanProperty(INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_UNPACK, true),

  @JvmField internal val validateModuleStructure: Boolean = parseBooleanValue(System.getProperty(VALIDATE_MODULES_STRUCTURE_PROPERTY, "false")),

  @JvmField internal val isUnpackedDist: Boolean = false,
) {
  companion object {
    /**
     * Use this property to change the project's compiled classes output directory.
     *
     * @see [org.jetbrains.intellij.build.impl.CompilationContextImpl.classesOutputDirectory]
     */
    const val PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY: String = "intellij.project.classes.output.directory"
    const val OS_LINUX: String = "linux"
    const val OS_WINDOWS: String = "windows"
    const val OS_MAC: String = "mac"
    const val OS_ALL: String = "all"
    const val OS_CURRENT: String = "current"

    /**
     * If this value is set no distributions of the product will be produced,
     * only [non-bundled plugins][ProductModulesLayout.pluginModulesToPublish] will be built.
     */
    const val OS_NONE: String = "none"

    /** Build actual searchableOptions.xml file. If skipped, the (possibly outdated) source version of the file will be used. */
    const val SEARCHABLE_OPTIONS_INDEX_STEP: String = "search_index"
    const val BROKEN_PLUGINS_LIST_STEP: String = "broken_plugins_list"
    const val PROVIDED_MODULES_LIST_STEP: String = "provided_modules_list"
    const val GENERATE_JAR_ORDER_STEP: String = "jar_order"
    const val SOURCES_ARCHIVE_STEP: String = "sources_archive"
    const val SCRAMBLING_STEP: String = "scramble"
    const val NON_BUNDLED_PLUGINS_STEP: String = "non_bundled_plugins"

    /** Build Maven artifacts for IDE modules. */
    const val MAVEN_ARTIFACTS_STEP: String = "maven_artifacts"

    /** Build macOS artifacts. */
    const val MAC_ARTIFACTS_STEP: String = "mac_artifacts"

    /** Build .dmg file for macOS. If skipped, only the .sit archive will be produced. */
    const val MAC_DMG_STEP: String = "mac_dmg"

    /**
     * Publish .sit file for macOS.
     * If skipped, only the .dmg archive will be produced.
     * If skipped together with [MAC_DMG_STEP], only the .zip archive will be produced.
     *
     * Note: .sit is required to build patches.
     */
    const val MAC_SIT_PUBLICATION_STEP: String = "mac_sit"

    /** Sign macOS distribution. */
    const val MAC_SIGN_STEP: String = "mac_sign"

    /** Notarize macOS distribution. */
    const val MAC_NOTARIZE_STEP: String = "mac_notarize"

    /** Build Linux artifacts. */
    const val LINUX_ARTIFACTS_STEP: String = "linux_artifacts"

    /** Build Linux tar.gz artifact without bundled Runtime. */
    const val LINUX_TAR_GZ_WITHOUT_BUNDLED_RUNTIME_STEP: String = "linux_tar_gz_without_jre"

    /** Build *.exe installer for Windows distribution. If skipped, only the .zip archive will be produced. */
    const val WINDOWS_EXE_INSTALLER_STEP: String = "windows_exe_installer"

    /** Sign *.exe files in Windows distribution. */
    const val WIN_SIGN_STEP: String = "windows_sign"

    const val LOCALIZE_STEP: String = "localize"

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

    /** Build Frankenstein artifacts. */
    const val CROSS_PLATFORM_DISTRIBUTION_STEP: String = "cross_platform_dist"

    /** Generate files containing lists of used third-party libraries  */
    const val THIRD_PARTY_LIBRARIES_LIST_STEP: String = "third_party_libraries"

    /** Build community distributives  */
    const val COMMUNITY_DIST_STEP: String = "community_dist"
    const val OS_SPECIFIC_DISTRIBUTIONS_STEP: String = "os_specific_distributions"
    const val PREBUILD_SHARED_INDEXES: String = "prebuild_shared_indexes"
    const val VERIFY_CLASS_FILE_VERSIONS: String = "verify_class_file_versions"

    const val ARCHIVE_PLUGINS: String = "archivePlugins"

    const val VALIDATE_PLUGINS_TO_BE_PUBLISHED: String = "validatePluginsToBePublished"

    /**
     * Publish artifacts to TeamCity storage while the build is still running, immediately after the artifacts are built.
     * Comprises many small publication steps.
     * Note: skipping this step won't affect the publication of 'Artifact paths' in TeamCity build settings and vice versa.
     */
    const val TEAMCITY_ARTIFACTS_PUBLICATION_STEP: String = "teamcity_artifacts_publication"

    /**
     * @see org.jetbrains.intellij.build.fus.createStatisticsRecorderBundledMetadataProviderTask
     */
    const val FUS_METADATA_BUNDLE_STEP: String = "fus_metadata_bundle_step"

    /**
     * @see org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
     */
    const val REPAIR_UTILITY_BUNDLE_STEP: String = "repair_utility_bundle_step"

    const val DOC_AUTHORING_ASSETS_STEP: String = "doc_authoring_assets"

    /**
     * By default, a build cleans up the output directory before compilation. Use this property to change the behavior.
     */
    const val CLEAN_OUTPUT_DIRECTORY_PROPERTY: String = "intellij.build.clean.output.root"

    /**
     * If `false` build scripts compile project classes to a special output directory (to not interfere with the default project output if
     * invoked on a developer machine).
     * If `true` compilation step is skipped and compiled classes from the project output are used instead.
     * True if [BuildOptions.isInDevelopmentMode] is enabled.
     *
     * @see [org.jetbrains.intellij.build.impl.CompilationContextImpl.classesOutputDirectory]
     */
    const val USE_COMPILED_CLASSES_PROPERTY: String = "intellij.build.use.compiled.classes"

    /**
     * By default, if the incremental compilation fails, a clean rebuild is attempted.
     */
    const val INCREMENTAL_COMPILATION_FALLBACK_REBUILD_PROPERTY: String = "intellij.build.incremental.compilation.fallback.rebuild"

    /**
     * If `true` then [org.jetbrains.intellij.build.impl.compilation.CompiledClasses] will be rebuilt from scratch
     */
    const val FORCE_REBUILD_PROPERTY: String = "intellij.jps.cache.rebuild.force"

    /**
     * Enables module structure validation, `false` by default.
     */
    const val VALIDATE_MODULES_STRUCTURE_PROPERTY: String = "intellij.build.module.structure"

    /**
     * Max number of dependency resolution retry attempts. `1` means no retries.
     *
     * @see [org.jetbrains.intellij.build.impl.JpsCompilationRunner.resolveProjectDependencies]
     */
    const val RESOLVE_DEPENDENCIES_MAX_ATTEMPTS_PROPERTY: String = "intellij.build.dependencies.resolution.retry.max.attempts"

    /**
     * Initial delay in milliseconds between dependency resolution retries on fault. Default is `1000`.
     *
     * @see [org.jetbrains.intellij.build.impl.JpsCompilationRunner.resolveProjectDependencies]
     */
    const val RESOLVE_DEPENDENCIES_DELAY_MS_PROPERTY: String = "intellij.build.dependencies.resolution.retry.delay.ms"
    const val TARGET_OS_PROPERTY: String = "intellij.build.target.os"

    /**
     * Use this system property to specify the target JVM architecture. 
     * Possible values are `x64`, `aarch64` and `current` (which refers to the architecture on which the build scripts are executed). 
     * If no value is provided, artifacts for all supported architectures will be built.  
     */
    const val TARGET_ARCH_PROPERTY: String = "intellij.build.target.arch"
    private const val ARCH_CURRENT: String = "current"

    /**
     * If `true`, the project modules will be compiled incrementally.
     */
    const val INTELLIJ_BUILD_INCREMENTAL_COMPILATION: String = "intellij.build.incremental.compilation"

    /**
     * Allows to override [ApplicationInfoProperties.isEAP].
     */
    const val INTELLIJ_BUILD_OVERRIDE_APPLICATION_VERSION_IS_EAP: String = "intellij.build.override.application.version.is.eap"

    /**
     * Allows to override [ApplicationInfoProperties.versionSuffix].
     */
    const val INTELLIJ_BUILD_OVERRIDE_APPLICATION_VERSION_SUFFIX: String = "intellij.build.override.application.version.suffix"

    /**
     * Allows to override [ApplicationInfoProperties.majorReleaseDate].
     */
    const val INTELLIJ_BUILD_OVERRIDE_APPLICATION_VERSION_MAJOR_RELEASE_DATE: String = "intellij.build.override.application.version.majorReleaseDate"

    /**
     * Pass comma-separated names of build steps (see below) to this system property to skip them.
     */
    const val BUILD_STEPS_TO_SKIP_PROPERTY: String = "intellij.build.skip.build.steps"

    /**
     * By default, the build process produces temporary and resulting files under `<projectHome>/out/<productName>` directory.
     * Use this property to change the output directory.
     */
    const val INTELLIJ_BUILD_OUTPUT_ROOT: String = "intellij.build.output.root"

    /**
     * @see [pathToCompiledClassesArchive]
     */
    const val INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE: String = "intellij.build.compiled.classes.archive"

    /**
     * @see [pathToCompiledClassesArchivesMetadata]
     */
    const val INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA: String = "intellij.build.compiled.classes.archives.metadata"

    /**
     * If `false` won't unpack downloaded jars with compiled classes from [INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA].
     */
    const val INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_UNPACK: String = "intellij.build.compiled.classes.archives.unpack"

    /**
     * By default, calculated based on the build number.
     */
    const val INTELLIJ_BUILD_IS_NIGHTLY: String = "intellij.build.is.nightly"

    /**
     * IJPL-176 Download pre-compiled IJent executables.
     */
    const val IJENT_EXECUTABLE_DOWNLOADING: String = "ijent.executable.downloading"

    private fun parseBooleanValue(text: String): Boolean = when {
      text.toBoolean() -> true
      text.equals(false.toString(), ignoreCase = true) -> false
      else -> throw IllegalArgumentException("Could not parse as boolean, accepted values are only 'true' or 'false': $text")
    }

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

    private val DEPENDENCIES_PROPERTIES: DependenciesProperties by lazy {
      DependenciesProperties(COMMUNITY_ROOT)
    }
  }

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
   * When `true`, attempts to locate a local debug build of cross-platform launcher.
   */
  var useLocalLauncher: Boolean = false

  /**
   * Pass `true` to this system property to produce .snap packages.
   * A build configuration should have "docker.version >= 17" in requirements.
   */
  var buildUnixSnaps: Boolean = SystemProperties.getBooleanProperty("intellij.build.unix.snaps", false)

  /**
   * Docker image for snap package creation
   */
  var snapDockerImage: String = System.getProperty("intellij.build.snap.docker.image") ?: DEPENDENCIES_PROPERTIES["snapDockerImage"]
  var snapDockerBuildTimeoutMin: Long = System.getProperty("intellij.build.snap.timeoutMin", "20").toLong()

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
   * Use [BuildContext.buildNumber] to get the actual build number in build scripts.
   * @see BuildContext.checkDistributionBuildNumber
   */
  var buildNumber: String? = run {
    val buildNumber = System.getProperty("build.number")
    if (buildNumber?.toIntOrNull() != null && TeamCityHelper.isUnderTeamCity) {
      // a build counter supplied by default in TeamCity cannot be used as a build number, skipping
      null
    }
    else buildNumber
  }

  /**
   * Use [BuildContext.pluginBuildNumber] to get the actual build number in build scripts.
   */
  var pluginBuildNumber: String? = buildNumber

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
   * If this option is set to `true` and [ProductProperties.rootModuleForModularLoader] is non-null, a file containing module descriptors 
   * will be added to the distribution (IJPL-109), and launchers will use it to start the IDE (IJPL-128).
   */
  @ApiStatus.Experimental
  var useModularLoader: Boolean = SystemProperties.getBooleanProperty("intellij.build.use.modular.loader", true)

  /**
   * If this option is set to `false`, [runtime module repository][com.intellij.platform.runtime.repository.RuntimeModuleRepository] won't be included in the installation.
   * It's supposed to be used only for development to speed up the building process a bit. 
   * Production builds must always include the module repository since tools like IntelliJ Platform Gradle Plugin and Plugin Verifier relies on it. 
   * This option doesn't make sense if [modular loader][BuildContext.useModularLoader] is used
   * (in this case, the generation is always enabled).
   */
  @ApiStatus.Experimental
  var generateRuntimeModuleRepository: Boolean = SystemProperties.getBooleanProperty("intellij.build.generate.runtime.module.repository", true)

  /**
   * Specifies a prefix to use when looking for an artifact of a [org.jetbrains.intellij.build.JetBrainsRuntimeDistribution] to be bundled with distributions.
   * If `null`, `"jbr_jcef-"` will be used.
   */
  var bundledRuntimePrefix: String? = System.getProperty("intellij.build.bundled.jre.prefix")

  /**
   * Enables "fastdebug" runtime.
   */
  var runtimeDebug: Boolean = parseBooleanValue(System.getProperty("intellij.build.bundled.jre.debug", "false"))

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

  @set:TestOnly
  @ApiStatus.Internal
  var buildStepListener: BuildStepListener = BuildStepListener()

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
    val targetArchProperty = System.getProperty(TARGET_ARCH_PROPERTY)?.takeIf { it.isNotBlank() }
    targetArch = if (targetArchProperty == ARCH_CURRENT) JvmArchitecture.currentJvmArch else targetArchProperty?.let(JvmArchitecture::valueOf)
    val randomSeedString = System.getProperty("intellij.build.randomSeed")
    randomSeedNumber = if (randomSeedString == null || randomSeedString.isBlank()) {
      ThreadLocalRandom.current().nextLong()
    }
    else {
      randomSeedString.toLong()
    }
  }

  private fun getSetProperty(name: String): Set<String> = System.getProperty(name)?.split(',')?.toSet() ?: emptySet()
}
