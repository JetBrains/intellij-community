// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * All paths are absolute and use '/' as a separator
 */
abstract class BuildPaths(
  val communityHomeDir: BuildDependenciesCommunityRoot,
  val buildOutputDir: Path,
  /**
   * All log and debug files should be written to this directory. It will be automatically published to TeamCity artifacts
   */
  val logDir: Path,
  /**
   * Path to a base directory of the project which will be compiled
   */
  val projectHome: Path
) {
  /**
   * Path to a directory where idea/community Git repository is checked out
   */
  val communityHome: String = FileUtilRt.toSystemIndependentName(communityHomeDir.communityRoot.pathString)

  /**
   * Path to a directory where build script will store temporary and resulting files
   */
  val buildOutputRoot: String = FileUtilRt.toSystemIndependentName(buildOutputDir.toString())

  /**
   * Path to a directory where resulting artifacts will be placed
   */
  lateinit var artifacts: String
  lateinit var artifactDir: Path

  /**
   * Path to a directory containing distribution files ('bin', 'lib', 'plugins' directories) common for all operating systems
   */
  var distAllDir: Path = buildOutputDir.resolve("dist.all")

  fun getDistAll(): String = FileUtilRt.toSystemIndependentName(distAllDir.toString())

  /**
   * Path to a directory where temporary files required for a particular build step can be stored
   */
  val tempDir: Path = buildOutputDir.resolve("temp")

  /**
   * Path to a directory where temporary files required for a particular build step can be stored
   */
  val temp: String = FileUtilRt.toSystemIndependentName(tempDir.toString())
}
