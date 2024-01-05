// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.history.core.Paths
import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.util.NlsSafe

abstract class ChangeSetActivityItem(changeSet: ChangeSet) : ActivityItem {
  override val timestamp: Long = changeSet.timestamp
  val id = changeSet.id
  abstract val name: @NlsSafe String?

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChangeSetActivityItem) return false
    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()
}

internal class ChangeActivityItem(changeSet: ChangeSet) : ChangeSetActivityItem(changeSet) {
  override val name = changeSet.name ?: changeSet.presentableNameFromPaths()
}

private fun ChangeSet.presentableNameFromPaths(): String? {
  val allPaths = affectedPaths
  if (allPaths.isEmpty()) return null

  val firstPathName = Paths.getNameOf(allPaths.first())
  if (allPaths.size == 1) return firstPathName
  return LocalHistoryBundle.message("activity.item.presentation.from.paths", firstPathName, allPaths.size - 1)
}

internal class LabelActivityItem(changeSet: ChangeSet) : ChangeSetActivityItem(changeSet) {
  override val name = changeSet.label ?: changeSet.name
  val color = changeSet.labelColor
}

fun ChangeSet.toActivityItem(): ActivityItem {
  if (isLabelOnly) return LabelActivityItem(this)
  return ChangeActivityItem(this)
}