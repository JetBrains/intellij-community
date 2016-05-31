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

/**
 * @author nik
 */
class BuildOptions {
  /**
   * By default build scripts compile project classes to a special output directory (to not interfere with the default project output if
   * invoked on a developer machine). Pass 'true' to this system property to skip compilation step and use compiled classes from the project output instead.
   */
  public static final String USE_COMPILED_CLASSES_PROPERTY = "intellij.build.useCompiledClasses"
  boolean useCompiledClassesFromProjectOutput = SystemProperties.getBooleanProperty(USE_COMPILED_CLASSES_PROPERTY, false)

  /**
   * Pass comma-separated names of build steps (see below) to 'intellij.build.skipBuildSteps' system property to skip them when building locally.
   */
  Set<String> buildStepsToSkip = System.getProperty("intellij.build.skipBuildSteps", "").split(",") as Set<String>
  static final SEARCHABLE_OPTIONS_INDEX_STEP = "search_index"
  static final SOURCES_ARCHIVE_STEP = "sources_archive"
  static final MAC_DISTRIBUTION_STEP = "mac_dist"
  static final LINUX_DISTRIBUTION_STEP = "linux_dist"
  static final WINDOWS_DISTRIBUTION_STEP = "windows_dist"
  static final CROSS_PLATFORM_DISTRIBUTION_STEP = "cross_platform_dist"
}