// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.fs.EelFileSystemApi.CreateTemporaryDirectoryOptions
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

fun EelPath.Absolute.toNioPath(eelApi: EelApi): Path = eelApi.mapper.toNioPath(this)

suspend fun EelPathMapper.maybeUploadPath(path: Path, scope: CoroutineScope): EelPath.Absolute {
  val options = CreateTemporaryDirectoryOptions.Builder()
    .prefix(path.fileName.toString())
    .suffix("eel")
    .deleteOnExit(true)
    .build()

  return maybeUploadPath(path, scope, options)
}

interface EelPathMapper {
  fun getOriginalPath(path: Path): EelPath.Absolute?

  /**
   * Transfers file system entry which is pointed to by [path] to the machine which owns this [EelPathMapper].
   *
   * The entry is transferred completely, i.e., if it is a directory, then it is copied recursively with contents.
   * Symbolic links are **not** followed.
   *
   * If [path] and [EelApi] are located on the same environment, then this function is equivalent to [getOriginalPath].
   * Otherwise, this function copies the entry to an implementation-defined location.
   *
   * [scope] is used to control the lifetime of the files that have been transferred. When [scope] is closed, the files will be deleted
   */
  suspend fun maybeUploadPath(
    path: Path,
    scope: CoroutineScope,
    options: CreateTemporaryDirectoryOptions,
  ): EelPath.Absolute

  fun toNioPath(path: EelPath.Absolute): Path
}