// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.ex

import com.intellij.openapi.application.PathManager
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.div
import kotlin.io.path.name

class PerProjectInstancePaths(private val projectStoreBaseDir: Path) {

  fun getSystemDir(): Path {
    return getSystemDir(PathManager.getSystemDir(), ProjectManagerEx.IS_CHILD_PROCESS, ::currentProjectStoreBaseDir, projectStoreBaseDir)
  }

  fun getConfigDir(): Path {
    return getConfigDir(PathManager.getConfigDir(), ProjectManagerEx.IS_CHILD_PROCESS, ::currentProjectStoreBaseDir, projectStoreBaseDir)
  }

  fun getPluginsDir(): Path {
    return getPluginsDir(
      PathManager.getPluginsDir(),
      PathManager.getConfigDir(),
      ProjectManagerEx.IS_CHILD_PROCESS,
      ::currentProjectStoreBaseDir,
      projectStoreBaseDir
    )
  }

  fun getLogDir(): Path {
    return getLogDir(
      PathManager.getLogDir(),
      PathManager.getSystemDir(),
      ProjectManagerEx.IS_CHILD_PROCESS,
      ::currentProjectStoreBaseDir,
      projectStoreBaseDir
    )
  }

  companion object {
    @VisibleForTesting
    fun getSystemDir(currentSystem: Path,
                     currentChildProcess: Boolean = false,
                     currentProjectStoreBaseDir: () -> Path? = { null },
                     newProjectStoreBaseDir: Path): Path {
      return adjustPathForNewProject(currentSystem, currentChildProcess, currentProjectStoreBaseDir, newProjectStoreBaseDir)
    }

    @VisibleForTesting
    fun getConfigDir(currentConfig: Path,
                     currentChildProcess: Boolean,
                     currentProjectStoreBaseDir: () -> Path?,
                     newProjectStoreBaseDir: Path): Path {
      return adjustPathForNewProject(currentConfig, currentChildProcess, currentProjectStoreBaseDir, newProjectStoreBaseDir)
    }

    @VisibleForTesting
    fun getPluginsDir(currentPlugins: Path,
                      currentConfig: Path,
                      currentChildProcess: Boolean,
                      currentProjectStoreBaseDir: () -> Path?,
                      newProjectStoreBaseDir: Path): Path {
      return if (currentPlugins.startsWith(currentConfig)) {
        val newConfig = getConfigDir(currentConfig, currentChildProcess, currentProjectStoreBaseDir, newProjectStoreBaseDir)
        newConfig.resolve(currentConfig.relativize(currentPlugins))
      }
      else {
        adjustPathForNewProject(currentPlugins, currentChildProcess, currentProjectStoreBaseDir, newProjectStoreBaseDir)
      }
    }

    @VisibleForTesting
    fun getLogDir(currentLog: Path,
                  currentSystem: Path,
                  currentChildProcess: Boolean,
                  currentProjectStoreBaseDir: () -> Path?,
                  newProjectStoreBaseDir: Path): Path {
      return if (currentLog.startsWith(currentSystem)) {
        val newSystem = getSystemDir(currentSystem, currentChildProcess, currentProjectStoreBaseDir, newProjectStoreBaseDir)
        newSystem.resolve(currentSystem.relativize(currentLog))
      }
      else {
        adjustPathForNewProject(currentLog, currentChildProcess, currentProjectStoreBaseDir, newProjectStoreBaseDir)
      }
    }

    private fun currentProjectStoreBaseDir(): Path? {
      val currentProject = ProjectManagerEx.getOpenProjects().singleOrNull()
      return currentProject?.basePath?.let { Paths.get(it) }
    }

    private fun adjustPathForNewProject(path: Path,
                                        currentChildProcess: Boolean,
                                        currentProjectStoreBaseDir: () -> Path?,
                                        newProjectStoreBaseDir: Path): Path {
      return if (currentChildProcess) {
        toPerProjectDir(toBaseDir(path, currentProjectStoreBaseDir()), newProjectStoreBaseDir)
      }
      else {
        toPerProjectDir(path, newProjectStoreBaseDir)
      }
    }

    private fun toPerProjectDir(base: Path, projectStoreBaseDir: Path): Path {
      return base / ProjectManagerEx.PER_PROJECT_SUFFIX / perProjectDirRelativePath(projectStoreBaseDir)
    }

    private fun perProjectDirRelativePath(projectStoreBaseDir: Path): Path {
      val absolute = projectStoreBaseDir.toAbsolutePath()
      return absolute.root.relativize(absolute)
    }

    private fun toBaseDir(perProjectDir: Path, projectStoreBaseDir: Path?): Path {
      return if (projectStoreBaseDir == null) {
        toBaseDir(perProjectDir)
      }
      else {
        toBaseDirFromProject(perProjectDir, projectStoreBaseDir)
      }
    }

    private fun toBaseDir(perProjectDir: Path): Path {
      var path: Path? = perProjectDir

      while (path != null && path.name != ProjectManagerEx.PER_PROJECT_SUFFIX) {
        path = path.parent
      }

      return requireNotNull(path?.parent) { "Illegal `process per project` path: $perProjectDir" }
    }

    private fun toBaseDirFromProject(perProjectDir: Path, projectStoreBaseDir: Path): Path {
      var path: Path? = perProjectDir
      var relativePath: Path? = perProjectDirRelativePath(projectStoreBaseDir)

      while (path != null && relativePath != null && path.name == relativePath.name) {
        path = path.parent
        relativePath = relativePath.parent
      }

      require(path != null && relativePath == null) { "Illegal `process per project` path: $perProjectDir, $projectStoreBaseDir" }

      return path.parent
    }
  }
}