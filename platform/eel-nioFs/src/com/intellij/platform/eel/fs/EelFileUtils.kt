// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.eel.fs

import com.intellij.platform.eel.EelSharedSecrets
import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path

/**
 * IJ platform specific utility functions for handling [Path].
 *
 * Unlike [EelFiles], these functions are supposed to replace specific functions of modules `intellij.platform.util` and similar modules.
 */
@ApiStatus.Experimental
object EelFileUtils {
  @Throws(IOException::class)
  @JvmStatic
  fun deleteRecursively(fileOrDirectory: Path) {
    EelSharedSecrets.platformUtilImpl.deleteRecursively(fileOrDirectory)
  }
}

/**
 * Returns the roots of this file system.
 *
 * On POSIX, the only root is `/`. On Windows, the roots are the fixed drives, for example `C:\`.
 * The list does not include network, removable, or optical drives: the IDE does not support a project on them,
 * and access to them can be slow.
 */
@ApiStatus.Internal
suspend fun EelFileSystemApi.getRoots(): Collection<EelPath> = when (val fs = this) {
  is EelFileSystemPosixApi -> listOf(EelPath.parse("/", descriptor))
  is EelFileSystemWindowsApi -> fs.getRootDirectories()
  // TODO: Fix it, see IJPL-261744
  else -> error("Unknown FS API $this")
}