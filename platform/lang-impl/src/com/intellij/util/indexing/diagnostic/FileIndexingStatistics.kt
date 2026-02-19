// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.indexing.ID

class FileIndexingStatistics(
  val fileType: FileType,
  val indexesProvidedByExtensions: Set<ID<*, *>>,
  val perIndexerEvaluateIndexValueTimes: Map<ID<*, *>, TimeNano>,
  val perIndexerEvaluatingIndexValueRemoversTimes: Map<ID<*, *>, TimeNano>,
  val indexesEvaluated: IndexesEvaluated,
)

enum class IndexesEvaluated {
  BY_EXTENSIONS,
  BY_USUAL_INDEXES,
  NOTHING_TO_WRITE,
}