// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.util.SystemProperties
import groovy.transform.CompileStatic

/**
 * @author nik
 */
@CompileStatic
class BuildOptions {
  /**
   * By default build scripts compile project classes to a special output directory (to not interfere with the default project output if
   * invoked on a developer machine). Pass 'true' to this system property to skip compilation step and use compiled classes from the project output instead.
   */
  public static final String USE_COMPILED_CLASSES_PROPERTY = "intellij.build.use.compiled.classes"
  boolean useCompiledClassesFromProjectOutput = SystemProperties.getBooleanProperty(USE_COMPILED_CLASSES_PROPERTY, false)

  /**
   * Specifies for which operating systems distributions should be built.
   */
  String targetOS = System.getProperty("intellij.build.target.os", OS_ALL)
  static final String OS_LINUX = "linux"
  static final String OS_WINDOWS = "windows"
  static final String OS_MAC = "mac"
  static final String OS_ALL = "all"
  /**
   * If this value is set no distributions of the product will be produced, only {@link ProductModulesLayout#setPluginModulesToPublish non-bundled plugins}
   * will be built.
   */
  static final String OS_NONE = "none"

  /**
   * Pass comma-separated names of build steps (see below) to 'intellij.build.skip.build.steps' system property to skip them when building locally.
   */
  Set<String> buildStepsToSkip = System.getProperty("intellij.build.skip.build.steps", "").split(",") as Set<String>
  /** Build actual searchableOptions.xml file. If skipped; the (possibly outdated) source version of the file will be used. */
  static final String SEARCHABLE_OPTIONS_INDEX_STEP = "search_index"
  static final String PROVIDED_MODULES_LIST_STEP = "provided_modules_list"
  static final String SOURCES_ARCHIVE_STEP = "sources_archive"
  static final String SCRAMBLING_STEP = "scramble"
  static final String NON_BUNDLED_PLUGINS_STEP = "non_bundled_plugins"
  /** Build Maven artifacts for IDE modules. */
  static final String MAVEN_ARTIFACTS_STEP = "maven_artifacts"
  /** Build macOS artifacts. */
  static final String MAC_ARTIFACTS_STEP = "mac_artifacts"
  /** Build .dmg file for macOS. If skipped, only .sit archive will be produced. */
  static final String MAC_DMG_STEP = "mac_dmg"
  /** Sign additional binary files in macOS distribution. */
  static final String MAC_SIGN_STEP = "mac_sign"
  /** Build Linux artifacts. */
  static final String LINUX_ARTIFACTS_STEP = "linux_artifacts"
  /** Build *.exe installer for Windows distribution. If skipped, only .zip archive will be produced. */
  static final String WINDOWS_EXE_INSTALLER_STEP = "windows_exe_installer"
  /** Build Frankenstein artifacts. */
  static final String CROSS_PLATFORM_DISTRIBUTION_STEP = "cross_platform_dist"
  /** Toolbox links generator step */
  static final String TOOLBOX_LITE_GEN_STEP = "toolbox_lite_gen"

  /**
   * Pass 'true' to this system property to produce an additional .dmg archive for macOS without bundled JRE.
   */
  public static final String BUILD_DMG_WITHOUT_BUNDLED_JRE = "intellij.build.dmg.without.bundled.jre"
  boolean buildDmgWithoutBundledJre = SystemProperties.getBooleanProperty(BUILD_DMG_WITHOUT_BUNDLED_JRE, SystemProperties.getBooleanProperty("artifact.mac.no.jdk", false))

  /**
   * Pass 'true' to this system property to produce .snap packages.
   * A build configuration should have "docker.version >= 17" in requirements.
   */
  boolean buildUnixSnaps = SystemProperties.getBooleanProperty("intellij.build.unix.snaps", false)

  /**
   * Image for snap package creation. Default is "snapcore/snapcraft:stable", but can be modified mostly due to problems
   * with new versions of snapcraft.
   */
  String snapDockerImage = System.getProperty("intellij.build.snap.docker.image", "snapcore/snapcraft:stable")

  /**
   * Path to a zip file containing 'production' and 'test' directories with compiled classes of the project modules inside.
   */
  String pathToCompiledClassesArchive = System.getProperty("intellij.build.compiled.classes.archive")

  /**
   * Path to a metadata file containing urls with compiled classes of the project modules inside.
   * Metadata is a {@linkplain org.jetbrains.intellij.build.impl.CompilationPartsMetadata} serialized into json format
   */
  String pathToCompiledClassesArchivesMetadata = System.getProperty("intellij.build.compiled.classes.archives.metadata")

  /**
   * If {@code true} the project modules will be compiled incrementally
   */
  boolean incrementalCompilation = SystemProperties.getBooleanProperty("intellij.build.incremental.compilation", false)

  /**
   * By default some build steps are executed in parallel threads. Set this property to {@code false} to disable this.
   */
  boolean runBuildStepsInParallel = SystemProperties.getBooleanProperty("intellij.build.run.steps.in.parallel", true)

  /**
   * Build number without product code (e.g. '162.500.10'), if {@code null} '&lt;baseline&gt;.SNAPSHOT' will be used. Use {@link BuildContext#buildNumber} to
   * get the actual build number in build scripts.
   */
  String buildNumber = System.getProperty("build.number")

  /**
   * By default build process produces temporary and resulting files under projectHome/out/productName directory, use this property to
   * change the output directory.
   */
  String outputRootPath = System.getProperty("intellij.build.output.root")

  /**
   * If {@code true} the build is running in 'Development mode' i.e. its artifacts aren't supposed to be used in production. In development
   * mode build scripts won't fail if some non-mandatory dependencies are missing and will just show warnings.
   * <p>By default 'development mode' is enabled if build is not running under continuous integration server (TeamCity).</p>
   */
  boolean isInDevelopmentMode = SystemProperties.getBooleanProperty("intellij.build.dev.mode",
                                                                    System.getProperty("teamcity.buildType.id") == null)

  /**
   * Specifies JRE version to be bundled with distributions, 8 by default.
   */
  int bundledJreVersion = System.getProperty("intellij.build.bundled.jre.version", "8").toInteger()

  /**
   * Specifies JRE build to be bundled with distributions. If {@code null} then jdkBuild from gradle.properties will be used.
   */
  String bundledJreBuild = System.getProperty("intellij.build.bundled.jre.build")

  /**
   * Directory path to unpack Jetbrains JDK builds into
   */
  static final String JDKS_TARGET_DIR_OPTION = "intellij.build.jdks.target.dir"
  String jdksTargetDir = System.getProperty(JDKS_TARGET_DIR_OPTION)
}