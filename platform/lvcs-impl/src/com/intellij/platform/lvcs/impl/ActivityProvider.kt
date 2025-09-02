// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.history.core.HistoryPathFilter
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ActivityProvider {
  fun getActivityItemsChanged(scope: ActivityScope): Flow<Unit>

  fun loadActivityList(scope: ActivityScope, filter: ActivityFilter?): ActivityData
  fun filterActivityList(scope: ActivityScope, data: ActivityData, filter: ActivityFilter?): Set<ActivityItem>?

  fun loadDiffData(scope: ActivityScope, selection: ActivitySelection, diffMode: DirectoryDiffMode): ActivityDiffData?
  fun loadSingleDiff(scope: ActivityScope, selection: ActivitySelection): DiffRequestProducer?

  fun getSupportedFilterKindFor(scope: ActivityScope): FilterKind

  fun getPresentation(item: ActivityItem): ActivityPresentation?
}

@ApiStatus.Experimental
data class ActivityFilter(val filePathFilter: HistoryPathFilter?,
                          val contentFilter: String?,
                          val showSystemLabels: Boolean)

@ApiStatus.Experimental
enum class FilterKind {
  FILE, CONTENT
}

@ApiStatus.Experimental
enum class DirectoryDiffMode {
  WithLocal, WithNext
}
