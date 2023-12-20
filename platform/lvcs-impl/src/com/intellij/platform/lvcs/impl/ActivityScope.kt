// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.history.core.Paths
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface ActivityScope {
  data object Recent : ActivityScope
  sealed interface File : ActivityScope {
    val file: VirtualFile
  }
  data class SingleFile(override val file: VirtualFile) : File
  data class Directory(override val file: VirtualFile) : File
  data class Selection(override val file: VirtualFile, val from: Int, val to: Int) : File

  companion object {
    @JvmStatic
    fun fromFile(f: VirtualFile): ActivityScope = if (f.isDirectory()) Directory(f) else SingleFile(f)
  }
}

val ActivityScope.File.filePath get() = Paths.createDvcsFilePath(file)
val ActivityScope.presentableName: String
  get() {
    return when (this) {
      is ActivityScope.Recent -> LocalHistoryBundle.message("activity.recent.tab.title")
      is ActivityScope.File -> file.presentableName
    }
  }