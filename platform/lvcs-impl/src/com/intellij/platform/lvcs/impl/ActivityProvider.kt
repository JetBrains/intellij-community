// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.diff.chains.DiffRequestProducer
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ActivityProvider {
  fun getActivityItemsChanged(scope: ActivityScope): Flow<Unit>

  fun loadActivityList(scope: ActivityScope, fileFilter: String?, showSystemLabels: Boolean): ActivityData
  fun filterActivityList(scope: ActivityScope, data: ActivityData, contentFilter: String?, showSystemLabels: Boolean): Set<ActivityItem>?

  fun loadDiffData(scope: ActivityScope, selection: ActivitySelection, diffMode: DirectoryDiffMode): ActivityDiffData?
  fun loadSingleDiff(scope: ActivityScope, selection: ActivitySelection): DiffRequestProducer?

  fun getSupportedFilterKindFor(scope: ActivityScope): FilterKind

  fun getPresentation(item: ActivityItem): ActivityPresentation?
}

@ApiStatus.Experimental
enum class FilterKind {
  FILE, CONTENT
}

@ApiStatus.Experimental
enum class DirectoryDiffMode {
  WithLocal, WithNext
}
