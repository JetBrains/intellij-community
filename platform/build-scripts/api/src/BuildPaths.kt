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

    /**
     * Path to the Ultimate repository root or null if it is the Community repository.
     */
    @JvmStatic
    val MAYBE_ULTIMATE_HOME: Path? by lazy {
      IdeaProjectLoaderUtil.maybeUltimateHome()
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
   * It was used before as a folder to output JPS artifacts.
   * Now we don't use JPS artifacts, but the fixed path is still used by some scripts.
   * It's recommended to migrate into [artifactDir] or [tempDir].
   */
  @Deprecated("Use [artifactDir] or [tempDir] instead")
  val jpsArtifacts: Path = buildOutputDir.resolve("jps-artifacts")
}