// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.indexing.ID

class FileIndexingStatistics(
  val fileType: FileType,
  val indexesProvidedByExtensions: Set<ID<*, *>>,
  val wasFullyIndexedByExtensions: Boolean,
  val perIndexerEvaluateIndexValueTimes: Map<ID<*, *>, TimeNano>,
  val perIndexerEvaluatingIndexValueRemoversTimes: Map<ID<*, *>, TimeNano>
)