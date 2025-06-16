// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.annotations.ApiStatus

/** Interface for [BaseFileTypeInputFilter] extension points -- to extend filter with custom file-exclusion rules */
@ApiStatus.Internal
interface IndexFilterExcludingExtension {
  /** files of this type this extension _may_ decide to exclude -- see [shouldExclude] */
  fun getFileType(): FileType

  fun shouldExclude(file: IndexedFile): Boolean
}