// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.io.FileUtilRt
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

import java.nio.file.Path
/**
 * All paths are absolute and use '/' as a separator
 */
@CompileStatic
abstract class BuildPaths {
  BuildPaths(@NotNull Path communityHomeDir, @NotNull Path buildOutputDir) {
    this.communityHomeDir = communityHomeDir

    this.buildOutputRoot = FileUtilRt.toSystemIndependentName(buildOutputDir.toString())
    communityHome = FileUtilRt.toSystemIndependentName(communityHomeDir.toString())

    tempDir = buildOutputDir.resolve("temp")
    temp = FileUtilRt.toSystemIndependentName(tempDir.toString())

    distAllDir = buildOutputDir.resolve("dist.all")
    distAll = FileUtilRt.toSystemIndependentName(distAllDir.toString())
  }

  /**
   * Path to a directory where idea/community Git repository is checked out
   */
  final String communityHome
  final Path communityHomeDir

  /**
   * Path to a base directory of the project which will be compiled
   */
  String projectHome

  /**
   * Path to a directory where build script will store temporary and resulting files
   */
  String buildOutputRoot

  /**
   * Path to a directory where resulting artifacts will be placed
   */
  String artifacts

  /**
   * Path to a directory containing distribution files ('bin', 'lib', 'plugins' directories) common for all operating systems
   */
  String distAll
  Path distAllDir

  /**
   * Path to a directory where temporary files required for a particular build step can be stored
   */
  final String temp
  final Path tempDir

  /**
   * Path to a directory containing JDK (currently Java 8) which is used to compile the project
   */
  String jdkHome

  /**
   * Path to a directory containing Kotlin plugin with compiler which is used to compile the project
   */
  String kotlinHome
}
