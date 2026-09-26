// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.local

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FileTypeUsageSummaryProvider {
  fun getFileTypeStats(): Map<String, FileTypeUsageSummary>
  fun updateFileTypeSummary(fileTypeName: String)
}

@ApiStatus.Internal
@Serializable
data class FileTypeUsageSummary(@JvmField var usageCount: Int, @JvmField var lastUsed: Long) {
  constructor() : this(0, 0)
}