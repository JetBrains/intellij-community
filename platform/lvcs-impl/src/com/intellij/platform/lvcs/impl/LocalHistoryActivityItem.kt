// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.history.core.revisions.RecentChange
import com.intellij.history.integration.ui.models.RevisionItem
import com.intellij.platform.lvcs.ActivityItem

class RevisionActivityItem(val revisionItem: RevisionItem) : ActivityItem {
  override val id: Long
    get() = revisionItem.revision.changeSetId ?: 0
  override val timestamp: Long
    get() = revisionItem.revision.timestamp
}

class RecentChangeActivityItem(val recentChange: RecentChange) : ActivityItem {
  override val id: Long
    get() = recentChange.revisionAfter.changeSetId ?: 0
  override val timestamp: Long
    get() = recentChange.revisionAfter.timestamp
}