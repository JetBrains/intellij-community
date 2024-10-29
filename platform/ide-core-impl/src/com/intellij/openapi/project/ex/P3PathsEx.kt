// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.ex

import com.intellij.openapi.application.PathManager
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

@ApiStatus.Experimental
@ApiStatus.Internal
class P3PathsEx(private val projectStoreBaseDir: Path) {
  companion object {
    @ApiStatus.Experimental
    const val PER_PROJECT_FOLDER: String = "INTERNAL_P3_FOLDER"

    fun getSystemDir(baseSystemDir: Path, projectStoreBaseDir: Path): Path {
      return getPerProjectDir(baseSystemDir, projectStoreBaseDir)
    }

    fun getConfigDir(baseSystemDir: Path, projectStoreBaseDir: Path): Path {
      return getPerProjectDir(baseSystemDir, projectStoreBaseDir)
    }

    fun getPluginsDir(): Path {
      return PathManager.getPluginsDir()
    }

    fun getLogDir(baseSystemDir: Path, projectStoreBaseDir: Path): Path {
      return getPerProjectDir(baseSystemDir, projectStoreBaseDir)
    }

    private fun getPerProjectDir(parent: Path, projectStoreBaseDir: Path): Path = parent.resolve(PER_PROJECT_FOLDER).resolve(projectLocationHash(projectStoreBaseDir))


    private fun projectLocationHash(projectStoreBaseDir: Path): String
    {
      return projectStoreBaseDir.name + "_"+
             // the same as in com.intellij.configurationStore.ProjectStoreBase.getLocationHash
             // todo is it ok to use hashCode? is it stable between different runs?
             Integer.toHexString(projectStoreBaseDir.invariantSeparatorsPathString.hashCode())
    }
  }

  fun getSystemDir(): Path {
    return Companion.getSystemDir(PathManager.getOriginalSystemDir(), projectStoreBaseDir)
  }

  fun getConfigDir(): Path {
    return Companion.getSystemDir(PathManager.getOriginalConfigDir(), projectStoreBaseDir)
  }

  fun getPluginsDir(): Path {
    return Companion.getPluginsDir()
  }

  fun getLogDir(): Path {
    return Companion.getLogDir(PathManager.getOriginalLogDir(), projectStoreBaseDir)
  }
}