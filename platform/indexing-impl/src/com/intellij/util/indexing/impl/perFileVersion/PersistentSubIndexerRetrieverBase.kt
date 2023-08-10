// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.perFileVersion

import com.intellij.util.indexing.IndexedFile
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.Experimental
@ApiStatus.Internal
interface PersistentSubIndexerRetrieverBase<SubIndexerVersion> {
  @Throws(IOException::class)
  fun getFileIndexerId(file: IndexedFile): Int
  fun getVersion(file: IndexedFile): SubIndexerVersion?
}