/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
   * Pass comma-separated names of build steps (see below) to 'intellij.build.skipBuildSteps' system property to skip them when building locally.
   */
  Set<String> buildStepsToSkip = System.getProperty("intellij.build.skip.build.steps", "").split(",") as Set<String>
  static final SEARCHABLE_OPTIONS_INDEX_STEP = "search_index"
  static final SOURCES_ARCHIVE_STEP = "sources_archive"
  static final MAC_DISTRIBUTION_STEP = "mac_dist"
  static final MAC_DMG_STEP = "mac_dmg"
  static final LINUX_DISTRIBUTION_STEP = "linux_dist"
  static final WINDOWS_DISTRIBUTION_STEP = "windows_dist"
  static final WINDOWS_EXE_INSTALLER_STEP = "windows_exe_installer"
  static final CROSS_PLATFORM_DISTRIBUTION_STEP = "cross_platform_dist"
  static final SCRAMBLING_STEP = "scramble"

  /**
   * Pass 'true' to this system property to produce an additional dmg archive for Mac OS without bundled JRE
   */
  public static final String BUILD_DMG_WITHOUT_BUNDLED_JRE = "intellij.build.dmg.without.bundled.jre"
  boolean buildDmgWithoutBundledJre = SystemProperties.getBooleanProperty(BUILD_DMG_WITHOUT_BUNDLED_JRE, SystemProperties.getBooleanProperty("artifact.mac.no.jdk", false))

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
}