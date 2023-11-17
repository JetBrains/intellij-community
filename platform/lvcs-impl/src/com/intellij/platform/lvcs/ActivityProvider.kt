// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs

import kotlinx.coroutines.flow.Flow

interface ActivityProvider {
  val activityItemsChanged: Flow<Unit>

  fun loadActivityList(scope: ActivityScope, scopeFilter: String?): List<ActivityItem>
  fun filterActivityList(scope: ActivityScope, items: List<ActivityItem>, activityFilter: String?): Set<Long>?
  fun loadDiffData(scope: ActivityScope, selection: ActivitySelection): ActivityDiffData?

  fun isScopeFilterSupported(scope: ActivityScope): Boolean
  fun isActivityFilterSupported(scope: ActivityScope): Boolean

  fun getPresentation(item: ActivityItem): ActivityPresentation?
}