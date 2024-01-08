// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

sealed interface ChangeSetSelection {
  val data: ActivityData

  val leftItem: ChangeSetActivityItem?
  val rightItem: ChangeSetActivityItem?

  data class Single(val changeSet: ChangeSetActivityItem, override val data: ActivityData) : ChangeSetSelection {
    override val leftItem: ChangeSetActivityItem get() = changeSet
    override val rightItem: ChangeSetActivityItem? get() = null
  }

  data class Interval(val leftChangeSet: ChangeSetActivityItem,
                      val rightChangeSet: ChangeSetActivityItem,
                      override val data: ActivityData) : ChangeSetSelection {
    override val leftItem: ChangeSetActivityItem get() = leftChangeSet
    override val rightItem: ChangeSetActivityItem get() = rightChangeSet
  }
}

val ChangeSetSelection.leftRevision: RevisionId get() = leftItem?.revisionId ?: RevisionId.Current
val ChangeSetSelection.rightRevision: RevisionId get() = rightItem?.revisionId ?: RevisionId.Current

fun ActivitySelection.toChangeSetSelection(): ChangeSetSelection? {
  if (selectedItems.isEmpty()) return null
  if (selectedItems.size == 1) {
    val selectedActivityItem = selectedItems.single()
    if (selectedActivityItem is ChangeSetActivityItem) {
      return ChangeSetSelection.Single(selectedActivityItem, data)
    }
    return null
  }

  val firstItem = selectedItems.first()
  val lastItem = selectedItems.last()
  if (firstItem is ChangeSetActivityItem && lastItem is ChangeSetActivityItem) {
    return ChangeSetSelection.Interval(lastItem, firstItem, data)
  }
  return null
}