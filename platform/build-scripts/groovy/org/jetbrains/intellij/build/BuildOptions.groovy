/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
   * If this value is set no distributions of the product will be produced, only {@link ProductModulesLayout#pluginModulesToPublish non-bundled plugins}
   * will be built.
   */
  static final String OS_NONE = "none"

  /**
   * Pass comma-separated names of build steps (see below) to 'intellij.build.skip.build.steps' system property to skip them when building locally.
   */
  Set<String> buildStepsToSkip = System.getProperty("intellij.build.skip.build.steps", "").split(",") as Set<String>
  /** generate actual searchableOptions.xml file. If it is skipped the version of this file located in sources will be used, it may be outdated. */
  static final SEARCHABLE_OPTIONS_INDEX_STEP = "search_index"
  static final PROVIDED_MODULES_LIST_STEP = "provided_modules_list"
  static final SOURCES_ARCHIVE_STEP = "sources_archive"
  /** product DMG file for Mac OS X. If it is skipped only sit archive will be produced. */
  static final MAC_DMG_STEP = "mac_dmg"
  /** sign additional binary files in Mac OS X distribution */
  static final MAC_SIGN_STEP = "mac_sign"
  /** create *.exe installer for Windows distribution. If it is skipped only zip archive will be produced. */
  static final WINDOWS_EXE_INSTALLER_STEP = "windows_exe_installer"
  static final CROSS_PLATFORM_DISTRIBUTION_STEP = "cross_platform_dist"
  static final SCRAMBLING_STEP = "scramble"
  static final NON_BUNDLED_PLUGINS_STEP = "non_bundled_plugins"

  /**
   * Pass 'true' to this system property to produce an additional dmg archive for Mac OS without bundled JRE
   */
  public static final String BUILD_DMG_WITHOUT_BUNDLED_JRE = "intellij.build.dmg.without.bundled.jre"
  boolean buildDmgWithoutBundledJre = SystemProperties.getBooleanProperty(BUILD_DMG_WITHOUT_BUNDLED_JRE, SystemProperties.getBooleanProperty("artifact.mac.no.jdk", false))

  /**
   * Pass 'true' to this system property to produce .snap packages.
   * A build configuration should have "docker.version >= 17" in requirements.
   */
  boolean buildUnixSnaps = SystemProperties.getBooleanProperty("intellij.build.unix.snaps", false)

  /**
   * Path to a zip file containing 'production' and 'test' directories with compiled classes of the project modules inside.
   */
  String pathToCompiledClassesArchive = System.getProperty("intellij.build.compiled.classes.archive")

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
}