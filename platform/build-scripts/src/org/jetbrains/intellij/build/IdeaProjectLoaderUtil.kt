// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object IdeaProjectLoaderUtil {
  private const val JPS_BOOTSTRAP_COMMUNITY_HOME_ENV_NAME = "JPS_BOOTSTRAP_COMMUNITY_HOME"
  private const val ULTIMATE_REPO_MARKER_FILE = ".ultimate.root.marker"
  private const val COMMUNITY_REPO_MARKER_FILE = "intellij.idea.community.main.iml"
  private const val INTELLIJ_BUILD_COMMUNITY_HOME_PATH = "intellij.build.community.home.path"
  private const val INTELLIJ_BUILD_ULTIMATE_HOME_PATH = "intellij.build.ultimate.home.path"

  /**
   * This method only for internal usage. Use [BuildPaths.ULTIMATE_HOME] instead.
   * @param klass must be a class inside idea home directory, the one with `file` protocol. Jar files in the maven directory aren't accepted.
   */
  @ApiStatus.Internal
  fun guessUltimateHome(klass: Class<*>): Path {
    val ultimateHomePathOverride = System.getProperty(INTELLIJ_BUILD_ULTIMATE_HOME_PATH)
    if (ultimateHomePathOverride != null) {
      val path = Paths.get(ultimateHomePathOverride)
      require(path.toFile().exists()) {
        ("Ultimate home path: '" + path
         + "' passed via system property: '" + INTELLIJ_BUILD_ULTIMATE_HOME_PATH + " not exists")
      }
      return path
    }
    val start = getSomeRoot(klass)
    var home: Path? = start
    while (home != null) {
      if (Files.exists(home.resolve(ULTIMATE_REPO_MARKER_FILE))) {
        return home
      }
      home = home.parent
    }

    throw IllegalArgumentException("Cannot guess ultimate project home from root '" + start + "'" +
                                   ", marker file '" + ULTIMATE_REPO_MARKER_FILE + "'")
  }

  /**
   * This method only for internal usage. Use [BuildPaths.COMMUNITY_ROOT] instead.
   * @param klass must be a class inside idea home directory, the one with `file` protocol. Jar files in the maven directory aren't accepted.
   */
  @ApiStatus.Internal
  fun guessCommunityHome(klass: Class<*>): BuildDependenciesCommunityRoot {
    val communityHomePathOverride = System.getProperty(INTELLIJ_BUILD_COMMUNITY_HOME_PATH)
    if (communityHomePathOverride != null) {
      val path = Paths.get(communityHomePathOverride)
      require(path.toFile().exists()) {
        ("Community home path: '" + path
         + "' passed via system property: '" + INTELLIJ_BUILD_COMMUNITY_HOME_PATH + " not exists")
      }
      return BuildDependenciesCommunityRoot(path)
    }
    val start = getSomeRoot(klass)
    var home: Path? = start

    while (home != null) {
      if (Files.exists(home.resolve(COMMUNITY_REPO_MARKER_FILE))) {
        return BuildDependenciesCommunityRoot(home)
      }

      if (Files.exists(home.resolve("community").resolve(COMMUNITY_REPO_MARKER_FILE))) {
        return BuildDependenciesCommunityRoot(home.resolve("community"))
      }

      home = home.parent
    }

    throw IllegalArgumentException("Cannot guess community project home from root '" + start + "'" +
                                   ", marker file '" + COMMUNITY_REPO_MARKER_FILE + "'")
  }

  private fun getSomeRoot(klass: Class<*>): Path {
    // Under jps-bootstrap home is already known, reuse it
    val communityHome = System.getenv(JPS_BOOTSTRAP_COMMUNITY_HOME_ENV_NAME)
    if (communityHome != null) {
      return Path.of(communityHome).normalize()
    }

    val path = getPathFromClass(klass)
    if (!path.toString().endsWith("class")) {
      val ideaHomePath = System.getProperty("idea.home.path")
      if (ideaHomePath != null) {
        return Path.of(ideaHomePath)
      }
      throw IllegalArgumentException(
        String.format("To guess idea home, you must provide class that resides in .class file inside of idea home dir. " +
                      "But provided %s resides in %s", klass, path))
    }
    return path
  }

  private fun getPathFromClass(klass: Class<*>): Path {
    val klassFileName = klass.getName().replace(klass.getPackageName() + ".", "")
    val classFileURL = klass.getResource("$klassFileName.class")
    checkNotNull(classFileURL) { "Could not get .class file location from class " + klass.getName() }
    return Path.of(UrlClassLoader.urlToFilePath(classFileURL.path))
  }
}
