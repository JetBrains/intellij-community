// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import java.nio.file.Path

/**
 * All paths are absolute and use '/' as a separator
 */
class BuildPaths(
  val communityHomeDirRoot: BuildDependenciesCommunityRoot,

  /**
   * Path to a directory where build script will store temporary and resulting files
   */
  val buildOutputDir: Path,
  /**
   * All log and debug files should be written to this directory. It will be automatically published to TeamCity artifacts
   */
  val logDir: Path,
  /**
   * Path to a base directory of the project which will be compiled
   */
  val projectHome: Path,
  /**
   * Path to a directory where resulting artifacts like installer distributions will be placed
   */
  val artifactDir: Path,
  /**
   * Path to a directory where temporary files required for a particular build step can be stored
   */
  val tempDir: Path,
  /**
   * Path to a directory where searchable options will be built in
   */
  val searchableOptionDir: Path = tempDir.resolve("searchable-options")
) {
  companion object {
    @JvmStatic
    val ULTIMATE_HOME: Path by lazy {
      IdeaProjectLoaderUtil.guessUltimateHome()
    }

    @JvmStatic
    val COMMUNITY_ROOT: BuildDependenciesCommunityRoot by lazy {
      IdeaProjectLoaderUtil.guessCommunityHome()
    }
  }

  /**
   * Path to a directory where idea/community Git repository is checked out
   */
  val communityHomeDir: Path = communityHomeDirRoot.communityRoot

  /**
   * Path to a directory containing distribution files ('bin', 'lib', 'plugins' directories) common for all operating systems
   */
  var distAllDir: Path = buildOutputDir.resolve("dist.all")

  /**
   * Build scripts use different folder to store JPS build artifacts
   * instead of 'out/classes/artifacts' which is IDE default folder for those artifacts.
   * Different folders are used not to affect incremental build of IDE.
   * Not to be confused with [artifactDir].
   */
  val jpsArtifacts: Path = buildOutputDir.resolve("jps-artifacts")
}
