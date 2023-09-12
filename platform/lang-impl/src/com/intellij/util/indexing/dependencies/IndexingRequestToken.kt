// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.intellij.openapi.vfs.VirtualFile

interface IndexingRequestToken {
  /**
   * Monotonically increasing number representing IndexingStamp
   */
  fun getFileIndexingStamp(file: VirtualFile): FileIndexingStamp
  fun mergeWith(other: IndexingRequestToken): IndexingRequestToken
}