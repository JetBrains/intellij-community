// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.diff.chains.DiffRequestProducer
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ActivityProvider {
  fun getActivityItemsChanged(scope: ActivityScope): Flow<Unit>

  fun loadActivityList(scope: ActivityScope, scopeFilter: String?): ActivityData
  fun filterActivityList(scope: ActivityScope, data: ActivityData, activityFilter: String?): Set<ActivityItem>?

  fun loadDiffData(scope: ActivityScope, selection: ActivitySelection): ActivityDiffData?
  fun loadSingleDiff(scope: ActivityScope, selection: ActivitySelection): DiffRequestProducer?

  fun isScopeFilterSupported(scope: ActivityScope): Boolean
  fun isActivityFilterSupported(scope: ActivityScope): Boolean

  fun getPresentation(item: ActivityItem): ActivityPresentation?
}