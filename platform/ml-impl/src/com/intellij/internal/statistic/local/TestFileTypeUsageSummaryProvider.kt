// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.local

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
@TestOnly
class TestFileTypeUsageSummaryProvider : FileTypeUsageSummaryProvider {
  private val stats = hashMapOf<String, FileTypeUsageSummary>()

  override fun getFileTypeStats(): Map<String, FileTypeUsageSummary> {
    return if (stats.isEmpty())
      emptyMap()
    else
      HashMap(stats)
  }

  override fun updateFileTypeSummary(fileTypeName: String) {
    return
  }

  fun setStats(fileType: String, summary: FileTypeUsageSummary): TestFileTypeUsageSummaryProvider {
    stats[fileType] = summary
    return this
  }

  fun clearStats(): TestFileTypeUsageSummaryProvider {
    stats.clear()
    return this
  }
}
