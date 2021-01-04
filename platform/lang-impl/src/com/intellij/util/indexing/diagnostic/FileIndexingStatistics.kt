// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.indexing.ID

class FileIndexingStatistics(
  val fileType: FileType,
  val indexesProvidedByExtensions: Set<ID<*, *>>,
  val wasFullyIndexedByExtensions: Boolean,
  val perIndexerUpdateTimes: Map<ID<*, *>, TimeNano>,
  val perIndexerDeleteTimes: Map<ID<*, *>, TimeNano>
) {
  val indexingTime: TimeNano
    get() = perIndexerUpdateTimes.values.sum() + perIndexerDeleteTimes.values.sum()
}