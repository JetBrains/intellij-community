// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.EelSharedSecrets
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
