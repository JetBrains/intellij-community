// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface Label {
  val id: String

  /**
   * Revert all changes up to this Label according to the local history
   * Prefer using [LocalHistory.revertToLabel]
   *
   * @param file file or directory that should be reverted
   */
  @ApiStatus.Obsolete
  @RequiresBackgroundThread
  @Throws(LocalHistoryException::class)
  fun revert(project: Project, file: VirtualFile)

  @RequiresBackgroundThread
  fun getByteContent(path: String): ByteContent?

  companion object {
    @ApiStatus.Internal
    @JvmField
    val NON_EXISTENT_ID: String = "NULL"

    @JvmField
    val NULL_INSTANCE: Label = object : Label {
      override val id: String = NON_EXISTENT_ID
      override fun revert(project: Project, file: VirtualFile) = Unit
      override fun getByteContent(path: String): ByteContent? = null
    }
  }
}
