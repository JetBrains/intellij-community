// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.application.PathManager
import com.intellij.util.lang.UrlClassLoader
import com.jetbrains.plugin.structure.base.utils.exists
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolute

internal object IdeaProjectLoaderUtil {
  private const val JPS_BOOTSTRAP_COMMUNITY_HOME_ENV_NAME = "JPS_BOOTSTRAP_COMMUNITY_HOME"
  private val ULTIMATE_REPO_MARKER_FILE = Path.of(".ultimate.root.marker")
  private val COMMUNITY_REPO_MARKER_FILE = Path.of("intellij.idea.community.main.iml")
  private const val INTELLIJ_BUILD_COMMUNITY_HOME_PATH = "intellij.build.community.home.path"
  private const val INTELLIJ_BUILD_ULTIMATE_HOME_PATH = "intellij.build.ultimate.home.path"

  private data class HomeSource(val moniker: String, val path: String?)

  // collect a list of locations from most relevant to least relevant
  private fun collectHomeSources(): List<HomeSource> = listOf(
    HomeSource(
      moniker = "property '$INTELLIJ_BUILD_ULTIMATE_HOME_PATH'",
      path = System.getProperty(INTELLIJ_BUILD_ULTIMATE_HOME_PATH),
    ),
    HomeSource(
      moniker = "property '$INTELLIJ_BUILD_COMMUNITY_HOME_PATH'",
      path = System.getProperty(INTELLIJ_BUILD_COMMUNITY_HOME_PATH),
    ),
    HomeSource(
      moniker = "env '$JPS_BOOTSTRAP_COMMUNITY_HOME_ENV_NAME'",
      path = System.getenv(JPS_BOOTSTRAP_COMMUNITY_HOME_ENV_NAME),
    ),
    HomeSource(
      moniker = "property '${PathManager.PROPERTY_HOME_PATH}'",
      path = System.getProperty(PathManager.PROPERTY_HOME_PATH),
    ),
    // Bazel workspace location
    HomeSource(
      moniker = "bazel env 'BUILD_WORKSPACE_DIRECTORY'",
      path = System.getenv("BUILD_WORKSPACE_DIRECTORY"),
    ),
    // current class jar or .class location
    HomeSource(
      moniker = "jar location",
      path = getPathFromClass(javaClass),
    ),
    HomeSource(
      moniker = "current directory",
      path = System.getProperty("user.dir"),
    ),
  )

  /**
   * This method only for internal usage. Use [BuildPaths.ULTIMATE_HOME] instead.
   */
  @ApiStatus.Internal
  fun guessUltimateHome(): Path {
    return searchForAnyMarkerFile(listOf(ULTIMATE_REPO_MARKER_FILE))
  }

  /**
   * This method only for internal usage. Use [BuildPaths.COMMUNITY_ROOT] instead.
   */
  @ApiStatus.Internal
  fun guessCommunityHome(): BuildDependenciesCommunityRoot {
    val directMarker = COMMUNITY_REPO_MARKER_FILE
    val inSubdirMarker = Path.of("community").resolve(COMMUNITY_REPO_MARKER_FILE)

    val root = searchForAnyMarkerFile(listOf(directMarker, inSubdirMarker))

    return when {
      root.resolve(directMarker).exists() -> root
      root.resolve(inSubdirMarker).exists() -> root.resolve("community")
      else -> error("should not happen")
    }.let { BuildDependenciesCommunityRoot(it) }
  }

  fun searchForAnyMarkerFile(markerFiles: Collection<Path>): Path {
    val homeSources = collectHomeSources()
    for (source in homeSources) {
      if (source.path == null) {
        continue
      }

      for (markerFile in markerFiles) {
        val home = searchForMarkerFileUpwards(Paths.get(source.path), markerFile)
        if (home != null) {
          if (System.getProperty("intellij.build.search.for.repo.marker.debug") != null) {
            println("Root found by ${source.moniker}: $home (resolved from ${source.path})")
          }
          return home.absolute()
        }
      }
    }

    error(
      "Cannot find marker file $markerFiles across sources:\n" +
      homeSources.joinToString("\n") { "  ${it.moniker}: ${it.path}" })
  }

  private fun searchForMarkerFileUpwards(start: Path, markerFile: Path): Path? {
    var current = start
    while (true) {
      if (current.resolve(markerFile).exists()) {
        return current
      }

      current = current.parent ?: return null
    }
  }

  private fun getPathFromClass(klass: Class<*>): String {
    val klassFileName = klass.getName().replace(klass.getPackageName() + ".", "")
    val classFileURL = klass.getResource("$klassFileName.class")
    checkNotNull(classFileURL) { "Could not get .class file location from class " + klass.getName() }
    return UrlClassLoader.urlToFilePath(classFileURL.path)
  }
}
