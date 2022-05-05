// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class BuildOptions {
  /**
   * If {@code false} build scripts compile project classes to a special output directory (to not interfere with the default project output if
   * invoked on a developer machine).
   * If {@code true} compilation step is skipped and compiled classes from the project output are used instead.
   * True if {@link BuildOptions#isInDevelopmentMode} is enabled.
   *
   * @see {@link org.jetbrains.intellij.build.impl.CompilationContextImpl#getProjectOutputDirectory}
   */
  public static final String USE_COMPILED_CLASSES_PROPERTY = "intellij.build.use.compiled.classes";
  public boolean useCompiledClassesFromProjectOutput = SystemProperties.getBooleanProperty(USE_COMPILED_CLASSES_PROPERTY, isInDevelopmentMode);

  /**
   * Use this property to change the project compiled classes output directory.
   *
   * @see {@link org.jetbrains.intellij.build.impl.CompilationContextImpl#getProjectOutputDirectory}
   */
  public static final String PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY = "intellij.project.classes.output.directory";
  public String projectClassesOutputDirectory = System.getProperty(PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY);

  /**
   * Specifies for which operating systems distributions should be built.
   */
  public String targetOS;
  public static final String OS_LINUX = "linux";
  public static final String OS_WINDOWS = "windows";
  public static final String OS_MAC = "mac";
  public static final String OS_ALL = "all";
  public static final String OS_CURRENT = "current";

  /**
   * If this value is set no distributions of the product will be produced, only {@link ProductModulesLayout#setPluginModulesToPublish non-bundled plugins}
   * will be built.
   */
  public static final String OS_NONE = "none";

  /**
   * Pass comma-separated names of build steps (see below) to this system property to skip them.
   */
  private static final String BUILD_STEPS_TO_SKIP_PROPERTY = "intellij.build.skip.build.steps";

  /**
   * Pass comma-separated names of build steps (see below) to {@link BuildOptions#BUILD_STEPS_TO_SKIP_PROPERTY} system property to skip them when building locally.
   */
  public Set<String> buildStepsToSkip = Arrays.stream(System.getProperty(BUILD_STEPS_TO_SKIP_PROPERTY, "").split(","))
    .filter(s -> !s.isBlank()).collect(Collectors.toSet());
  /** Pre-builds SVG icons for all SVG resource files into *.jpix resources to speedup icons loading at runtime */
  public static final String SVGICONS_PREBUILD_STEP = "svg_icons_prebuild";
  /** Build actual searchableOptions.xml file. If skipped; the (possibly outdated) source version of the file will be used. */
  public static final String SEARCHABLE_OPTIONS_INDEX_STEP = "search_index";
  public static final String BROKEN_PLUGINS_LIST_STEP = "broken_plugins_list";
  public static final String PROVIDED_MODULES_LIST_STEP = "provided_modules_list";
  public static final String GENERATE_JAR_ORDER_STEP = "jar_order";
  public static final String SOURCES_ARCHIVE_STEP = "sources_archive";
  public static final String SCRAMBLING_STEP = "scramble";
  public static final String NON_BUNDLED_PLUGINS_STEP = "non_bundled_plugins";
  /** Build Maven artifacts for IDE modules. */
  public static final String MAVEN_ARTIFACTS_STEP = "maven_artifacts";
  /** Build macOS artifacts. */
  public static final String MAC_ARTIFACTS_STEP = "mac_artifacts";
  /** Build .dmg file for macOS. If skipped, only .sit archive will be produced. */
  public static final String MAC_DMG_STEP = "mac_dmg";
  /** Sign macOS distribution. */
  public static final String MAC_SIGN_STEP = "mac_sign";
  /** Build Linux artifacts. */
  public static final String LINUX_ARTIFACTS_STEP = "linux_artifacts";
  /** Build Linux tar.gz artifact without bundled JRE. */
  public static final String LINUX_TAR_GZ_WITHOUT_BUNDLED_JRE_STEP = "linux_tar_gz_without_jre";
  /** Build *.exe installer for Windows distribution. If skipped, only .zip archive will be produced. */
  public static final String WINDOWS_EXE_INSTALLER_STEP = "windows_exe_installer";
  /** Sign *.exe files in Windows distribution. */
  public static final String WIN_SIGN_STEP = "windows_sign";
  public static final Map<String,String> WIN_SIGN_OPTIONS =
    Arrays.stream(System.getProperty("intellij.build.win.sign.options", "")
      .split(";"))
      .filter(s -> !s.isBlank())
      .map(s -> {
        String[] item = s.split("=", 2);
        if (item.length != 2) {
          throw new IllegalArgumentException("Could not split by '=': " + s);
        }
        return item;
      })
      .collect(Collectors.toMap(item -> item[0], item -> item[1]));
  /** Build Frankenstein artifacts. */
  public static final String CROSS_PLATFORM_DISTRIBUTION_STEP = "cross_platform_dist";
  /** Toolbox links generator step */
  public static final String TOOLBOX_LITE_GEN_STEP = "toolbox_lite_gen";
  /** Generate files containing lists of used third-party libraries */
  public static final String THIRD_PARTY_LIBRARIES_LIST_STEP = "third_party_libraries";
  /** Build community distributives */
  public static final String COMMUNITY_DIST_STEP = "community_dist";
  public static final String OS_SPECIFIC_DISTRIBUTIONS_STEP = "os_specific_distributions";
  public static final String PREBUILD_SHARED_INDEXES = "prebuild_shared_indexes";
  public static final String SETUP_BUNDLED_MAVEN = "setup_bundled_maven";
  public static final String VERIFY_CLASS_FILE_VERSIONS = "verify_class_file_versions";
  /**
   * Publish artifacts to TeamCity storage while the build is still running, immediately after the artifacts are built.
   * Comprises many small publication steps.
   * Note: skipping this step won't affect publication of 'Artifact paths' in TeamCity build settings and vice versa
   */
  public static final String TEAMCITY_ARTIFACTS_PUBLICATION_STEP = "teamcity_artifacts_publication";
  /**
   * @see org.jetbrains.intellij.build.fus.StatisticsRecorderBundledMetadataProvider
   */
  public static final String FUS_METADATA_BUNDLE_STEP = "fus_metadata_bundle_step";

  /**
   * @see org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
   */
  public static final String REPAIR_UTILITY_BUNDLE_STEP = "repair_utility_bundle_step";

  /**
   * Pass 'true' to this system property to produce an additional .dmg archive for macOS without bundled JRE.
   */
  public static final String BUILD_DMG_WITHOUT_BUNDLED_JRE = "intellij.build.dmg.without.bundled.jre";
  public boolean buildDmgWithoutBundledJre = SystemProperties.getBooleanProperty(BUILD_DMG_WITHOUT_BUNDLED_JRE, SystemProperties.getBooleanProperty("artifact.mac.no.jdk", false));

  /**
   * Pass 'false' to this system property to skip building .dmg with bundled JRE.
   */
  public static final String BUILD_DMG_WITH_BUNDLED_JRE = "intellij.build.dmg.with.bundled.jre";
  public boolean buildDmgWithBundledJre = SystemProperties.getBooleanProperty(BUILD_DMG_WITH_BUNDLED_JRE, true);

  /**
   * Pass 'true' to this system property to produce .snap packages.
   * A build configuration should have "docker.version >= 17" in requirements.
   */
  public boolean buildUnixSnaps = SystemProperties.getBooleanProperty("intellij.build.unix.snaps", false);

  /**
   * Image for snap package creation. Default is "snapcore/snapcraft:stable", but can be modified mostly due to problems
   * with new versions of snapcraft.
   */
  public String snapDockerImage = System.getProperty("intellij.build.snap.docker.image", "snapcore/snapcraft:stable");

  /**
   * Path to a zip file containing 'production' and 'test' directories with compiled classes of the project modules inside.
   */
  public String pathToCompiledClassesArchive = System.getProperty("intellij.build.compiled.classes.archive");

  /**
   * Path to a metadata file containing urls with compiled classes of the project modules inside.
   * Metadata is a {@linkplain org.jetbrains.intellij.build.impl.compilation.CompilationPartsMetadata} serialized into json format
   */
  public String pathToCompiledClassesArchivesMetadata = System.getProperty("intellij.build.compiled.classes.archives.metadata");

  /**
   * If {@code true} the project modules will be compiled incrementally
   */
  public boolean incrementalCompilation = SystemProperties.getBooleanProperty("intellij.build.incremental.compilation", false);

  /**
   * By default some build steps are executed in parallel threads. Set this property to {@code false} to disable this.
   */
  public boolean runBuildStepsInParallel = SystemProperties.getBooleanProperty("intellij.build.run.steps.in.parallel", true);

  /**
   * Build number without product code (e.g. '162.500.10'), if {@code null} '&lt;baseline&gt;.SNAPSHOT' will be used. Use {@link BuildContext#buildNumber} to
   * get the actual build number in build scripts.
   */
  public String buildNumber = System.getProperty("build.number");

  /**
   * By default build process produces temporary and resulting files under projectHome/out/productName directory, use this property to
   * change the output directory.
   */
  public String outputRootPath = System.getProperty("intellij.build.output.root");

  public String logPath = System.getProperty("intellij.build.log.root");

  /**
   * If {@code true} write a separate compilation.log for all compilation messages
   */
  public Boolean compilationLogEnabled = SystemProperties.getBooleanProperty("intellij.build.compilation.log.enabled", true);

  /**
   * By default, build cleanup output folder before compilation, use this property to change this behaviour.
   */
  public static final String CLEAN_OUTPUT_FOLDER_PROPERTY = "intellij.build.clean.output.root";
  public boolean cleanOutputFolder = SystemProperties.getBooleanProperty(CLEAN_OUTPUT_FOLDER_PROPERTY, true);

  /**
   * If {@code true} the build is running in 'Development mode' i.e. its artifacts aren't supposed to be used in production. In development
   * mode build scripts won't fail if some non-mandatory dependencies are missing and will just show warnings.
   * <p>By default 'development mode' is enabled if build is not running under continuous integration server (TeamCity).</p>
   */
  public boolean isInDevelopmentMode = SystemProperties.getBooleanProperty("intellij.build.dev.mode",
                                                                    System.getenv("TEAMCITY_VERSION") == null);
  /**
   * If {@code true} the build is running as a unit test
   */
  public boolean isTestBuild = SystemProperties.getBooleanProperty("intellij.build.test.mode", false);

  public boolean skipDependencySetup = false;

  /**
   * Specifies list of names of directories of bundled plugins which shouldn't be included into the product distribution. This option can be
   * used to speed up updating the IDE from sources.
   */
  public Set<String> bundledPluginDirectoriesToSkip = Set.of(System.getProperty("intellij.build.bundled.plugin.dirs.to.skip", "").split(","));

  /**
   * Specifies list of names of directories of non-bundled plugins (determined by {@link ProductModulesLayout#pluginsToPublish} and
   * {@link ProductModulesLayout#buildAllCompatiblePlugins}) which should be actually built. This option can be used to speed up updating
   * the IDE from sources. By default all plugins determined by {@link ProductModulesLayout#pluginsToPublish} and
   * {@link ProductModulesLayout#buildAllCompatiblePlugins} are built. In order to skip building all non-bundled plugins, set the property to
   * {@code none}.
   */
  public List<String> nonBundledPluginDirectoriesToInclude = StringUtil.split(System.getProperty("intellij.build.non.bundled.plugin.dirs.to.include", ""), ",");

  /**
   * Specifies {@link org.jetbrains.intellij.build.JetBrainsRuntimeDistribution} build to be bundled with distributions. If {@code null} then {@code runtimeBuild} from gradle.properties will be used.
   */
  public String bundledRuntimeBuild = System.getProperty("intellij.build.bundled.jre.build");

  /**
   * Specifies a prefix to use when looking for an artifact of a {@link org.jetbrains.intellij.build.JetBrainsRuntimeDistribution} to be bundled with distributions.
   * If {@code null}, {@code "jbr_jcef-"} will be used.
   */
  public String bundledRuntimePrefix = System.getProperty("intellij.build.bundled.jre.prefix");

  /**
   * Enables fastdebug runtime
   */
  public boolean runtimeDebug = parseBooleanValue(System.getProperty("intellij.build.bundled.jre.debug", "false"));

  /**
   * Specifies an algorithm to build distribution checksums.
   */
  public String hashAlgorithm = "SHA-384";

  /**
   * Enables module structure validation, false by default
   */
  public static final String VALIDATE_MODULES_STRUCTURE_PROPERTY = "intellij.build.module.structure";
  public boolean validateModuleStructure = parseBooleanValue(System.getProperty(VALIDATE_MODULES_STRUCTURE_PROPERTY, "false"));

  @ApiStatus.Internal
  public boolean compressNonBundledPluginArchive = true;

  /**
   * Max attempts of dependencies resolution on fault. "1" means no retries.
   *
   * @see {@link org.jetbrains.intellij.build.impl.JpsCompilationRunner#resolveProjectDependencies}
   */
  public static final String RESOLVE_DEPENDENCIES_MAX_ATTEMPTS_PROPERTY = "intellij.build.dependencies.resolution.retry.max.attempts";
  public int resolveDependenciesMaxAttempts = Integer.parseInt(System.getProperty(RESOLVE_DEPENDENCIES_MAX_ATTEMPTS_PROPERTY, "2"));

  /**
   * Initial delay in milliseconds between dependencies resolution retries on fault. Default is 1000
   *
   * @see {@link org.jetbrains.intellij.build.impl.JpsCompilationRunner#resolveProjectDependencies}
   */
  public static final String RESOLVE_DEPENDENCIES_DELAY_MS_PROPERTY = "intellij.build.dependencies.resolution.retry.delay.ms";
  public long resolveDependenciesDelayMs = Long.parseLong(System.getProperty(RESOLVE_DEPENDENCIES_DELAY_MS_PROPERTY, "1000"));

  public static final String TARGET_OS_PROPERTY = "intellij.build.target.os";

  /**
   * See https://reproducible-builds.org/specs/source-date-epoch/
   */
  public long buildDateInSeconds;
  public long randomSeedNumber;

  public BuildOptions() {
    targetOS = System.getProperty(TARGET_OS_PROPERTY);
    if (OS_CURRENT.equals(targetOS)) {
      targetOS = SystemInfo.isWindows ? OS_WINDOWS :
                 SystemInfo.isMac ? OS_MAC :
                 SystemInfo.isLinux ? OS_LINUX : null;
    }
    else if (targetOS == null || targetOS.isEmpty()) {
      targetOS = OS_ALL;
    }

    String sourceDateEpoch = System.getenv("SOURCE_DATE_EPOCH");
    if (sourceDateEpoch == null) {
      buildDateInSeconds = System.currentTimeMillis() / 1000;
    }
    else {
      buildDateInSeconds = Long.parseLong(sourceDateEpoch);
    }

    String randomSeedString = System.getProperty("intellij.build.randomSeed");
    if (randomSeedString == null || randomSeedString.isBlank()) {
      randomSeedNumber = ThreadLocalRandom.current().nextLong();
    }
    else {
      randomSeedNumber = Long.parseLong(randomSeedString);
    }
  }

  private static Boolean parseBooleanValue(@NotNull String text) {
    if (StringUtil.equalsIgnoreCase(text, Boolean.TRUE.toString())) {
      return true;
    }

    if (StringUtil.equalsIgnoreCase(text, Boolean.FALSE.toString())) {
      return false;
    }

    throw new IllegalArgumentException("Could not parse as boolean, accepted values are only 'true' or 'false': " + text);
  }
}
