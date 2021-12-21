// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.io.FileUtilRt
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot

import java.nio.file.Path
/**
 * All paths are absolute and use '/' as a separator
 */
@CompileStatic
abstract class BuildPaths {
  BuildPaths(@NotNull Path communityHomeDir, @NotNull Path buildOutputDir, @NotNull Path logDir) {
    this.communityHomeDir = communityHomeDir

    this.buildOutputRoot = FileUtilRt.toSystemIndependentName(buildOutputDir.toString())
    communityHome = FileUtilRt.toSystemIndependentName(communityHomeDir.toString())
    this.buildDependenciesCommunityRoot = new BuildDependenciesCommunityRoot(communityHomeDir)

    tempDir = buildOutputDir.resolve("temp")
    temp = FileUtilRt.toSystemIndependentName(tempDir.toString())

    distAllDir = buildOutputDir.resolve("dist.all")

    this.logDir = logDir
    this.buildOutputDir = buildOutputDir
  }

  /**
   * Path to a directory where idea/community Git repository is checked out
   */
  final String communityHome
  final Path communityHomeDir
  final BuildDependenciesCommunityRoot buildDependenciesCommunityRoot

  /**
   * Path to a base directory of the project which will be compiled
   */
  String projectHome
  Path projectHomeDir

  /**
   * Path to a directory where build script will store temporary and resulting files
   */
  String buildOutputRoot
  Path buildOutputDir

  /**
   * All log and debug files should be written to this directory. It will be automatically published to TeamCity artifacts
   */
  final Path logDir

  /**
   * Path to a directory where resulting artifacts will be placed
   */
  String artifacts
  Path artifactDir

  /**
   * Path to a directory containing distribution files ('bin', 'lib', 'plugins' directories) common for all operating systems
   */
  Path distAllDir

  String getDistAll() {
    return distAllDir.toString().replace('\\', '/')
  }

  /**
   * Path to a directory where temporary files required for a particular build step can be stored
   */
  final String temp
  final Path tempDir
}
