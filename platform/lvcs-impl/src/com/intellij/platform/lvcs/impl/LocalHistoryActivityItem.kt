// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.history.core.revisions.RecentChange
import com.intellij.history.core.revisions.Revision
import com.intellij.util.containers.HashingStrategy

internal class RevisionActivityItem(val revision: Revision) : ActivityItem {
  override val timestamp: Long
    get() = revision.timestamp

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RevisionActivityItem) return false

    return RevisionHashingStrategy.equals(revision, other.revision)
  }

  override fun hashCode(): Int {
    return RevisionHashingStrategy.hashCode(revision)
  }
}

internal class RecentChangeActivityItem(val recentChange: RecentChange) : ActivityItem {
  override val timestamp: Long
    get() = recentChange.revisionAfter.timestamp

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RecentChangeActivityItem) return false

    return RevisionHashingStrategy.equals(recentChange.revisionAfter, other.recentChange.revisionAfter) &&
           RevisionHashingStrategy.equals(recentChange.revisionBefore, other.recentChange.revisionBefore)
  }

  override fun hashCode(): Int {
    return 31 * RevisionHashingStrategy.hashCode(recentChange.revisionAfter) +
           RevisionHashingStrategy.hashCode(recentChange.revisionBefore)
  }
}

private object RevisionHashingStrategy : HashingStrategy<Revision> {
  override fun hashCode(`object`: Revision): Int {
    return `object`.changeSetId.hashCode()
  }

  override fun equals(o1: Revision, o2: Revision): Boolean {
    return o1.changeSetId == o2.changeSetId
  }
}