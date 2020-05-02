// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.intellij.util.indexing.ID

class FileIndexingStatistics(
  val totalTime: TimeNano,
  val perIndexerTimes: Map<ID<*, *>, TimeNano>
) 