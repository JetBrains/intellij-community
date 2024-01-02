// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.history.core.revisions.CurrentRevision
import com.intellij.history.core.revisions.Revision
import com.intellij.history.core.tree.Entry

data class RevisionSelection(val leftRevision: Revision,
                             val rightRevision: Revision,
                             val allRevisions: List<Revision>) {
  val leftEntry: Entry? by lazy { leftRevision.findEntry() }
  val rightEntry: Entry? by lazy { rightRevision.findEntry() }
}

private fun ActivityScope.File.createCurrentRevision(revision: Revision): CurrentRevision {
  return CurrentRevision(revision.root, filePath.path)
}

fun ActivityScope.File.createCurrentRevision(revisionSelection: RevisionSelection): Revision {
  return createCurrentRevision(revisionSelection.leftRevision)
}

fun ActivitySelection.toRevisionSelection(scope: ActivityScope): RevisionSelection? {
  if (selectedItems.isEmpty()) return null
  if (selectedItems.size == 1) {
    val selectedActivityItem = selectedItems.single()
    if (selectedActivityItem is RevisionActivityItem && scope is ActivityScope.File) {
      val revision = selectedActivityItem.revision
      return RevisionSelection(revision, scope.createCurrentRevision(revision), revisions())
    }
    if (selectedActivityItem is RecentChangeActivityItem) {
      val recentChange = selectedActivityItem.recentChange
      return RevisionSelection(recentChange.revisionBefore, recentChange.revisionAfter, emptyList())
    }
    return null
  }

  val firstItem = selectedItems.first()
  val lastItem = selectedItems.last()
  if (firstItem is RevisionActivityItem && lastItem is RevisionActivityItem) {
    return RevisionSelection(lastItem.revision, firstItem.revision, revisions())
  }
  if (firstItem is RecentChangeActivityItem && lastItem is RecentChangeActivityItem) {
    return RevisionSelection(lastItem.recentChange.revisionBefore, firstItem.recentChange.revisionAfter, emptyList())
  }
  return null
}

private fun ActivitySelection.revisions(): List<Revision> {
  return allItems.filterIsInstance<RevisionActivityItem>().map { it.revision }
}