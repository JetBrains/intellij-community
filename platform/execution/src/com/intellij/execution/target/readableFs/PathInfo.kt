// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target.readableFs

import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Abstraction over target path because target paths (like ssh or wsl) can't always be represented as [Path].
 */
sealed class PathInfo {
  data class Directory(val empty: Boolean) : PathInfo()
  data class RegularFile(val executable: Boolean) : PathInfo()
  companion object {
    val localPathInfoProvider: TargetConfigurationReadableFs = TargetConfigurationReadableFs { getPathInfoForLocalPath(Path.of(it)) }
    fun getPathInfoForLocalPath(localPath: Path): PathInfo? =
      when {
        (!localPath.exists()) -> tryGetUsingOldApi(localPath.toFile())
        localPath.isRegularFile() -> RegularFile(localPath.isExecutable())
        localPath.isDirectory() -> Directory(localPath.listDirectoryEntries().isEmpty())
        else -> null
      }

    private fun tryGetUsingOldApi(file: File): PathInfo? = when {
      (!file.exists())-> null
      file.isFile -> RegularFile(file.canExecute())
      file.isDirectory -> Directory(file.list().isEmpty())
      else -> null
    }

  }
}