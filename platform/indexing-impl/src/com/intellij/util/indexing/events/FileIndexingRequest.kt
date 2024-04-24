// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FileIndexingRequest private constructor(
  val isDeleteRequest: Boolean,
  val file: VirtualFile,
  val fileId: Int = FileBasedIndex.getFileId(file),
) {
  companion object {
    private val LOG = Logger.getInstance(FileIndexingRequest::class.java)

    @JvmStatic
    fun updateRequest(file: VirtualFile): FileIndexingRequest {
      LOG.assertTrue(file is VirtualFileWithId, "Not a VirtualFileWithId: $file")
      return FileIndexingRequest(isDeleteRequest = false, file)
    }

    @JvmStatic
    fun deleteRequest(file: VirtualFile): FileIndexingRequest {
      LOG.assertTrue(file is VirtualFileWithId, "Not a VirtualFileWithId: $file")
      return FileIndexingRequest(isDeleteRequest = true, file)
    }
  }
}