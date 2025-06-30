// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.util.List

abstract class RefreshSession {
  abstract fun addFile(file: VirtualFile)

  abstract fun addAllFiles(files: Collection<VirtualFile>)

  fun addAllFiles(vararg files: VirtualFile) {
    addAllFiles(listOf(*files))
  }

  abstract fun launch()

  abstract fun cancel()

  @ApiStatus.Internal
  abstract fun metric(key: String): Any?
}
