// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.history.ActivityId
import com.intellij.history.core.Paths
import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe

abstract class ChangeSetActivityItem(changeSet: ChangeSet) : ActivityItem {
  override val timestamp: Long = changeSet.timestamp
  val id = changeSet.id
  val activityId: ActivityId? = changeSet.activityId
  abstract val name: @NlsSafe String?

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChangeSetActivityItem) return false
    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()
}

internal class ChangeActivityItem(changeSet: ChangeSet, scope: ActivityScope) : ChangeSetActivityItem(changeSet) {
  override val name = getName(changeSet, scope)

  private fun getName(changeSet: ChangeSet, scope: ActivityScope): @NlsContexts.Label String? {
    if (changeSet.name != null) return changeSet.name
    if (!scope.hasMultipleFiles) return LocalHistoryBundle.message("activity.item.presentation")
    return changeSet.presentableNameFromPaths()
  }
}

private fun ChangeSet.presentableNameFromPaths(): @NlsContexts.Label String? {
  val allPaths = affectedPaths
  if (allPaths.isEmpty()) return null

  val firstPathName = Paths.getNameOf(allPaths.first())
  if (allPaths.size == 1) return LocalHistoryBundle.message("activity.item.presentation.from.path", firstPathName)
  return LocalHistoryBundle.message("activity.item.presentation.from.paths", firstPathName, allPaths.size - 1)
}

internal class LabelActivityItem(changeSet: ChangeSet) : ChangeSetActivityItem(changeSet) {
  override val name = shorten(changeSet.label ?: changeSet.name)
  val color = changeSet.labelColor

  private fun shorten(name: String?): String? {
    if (name == null) return null
    if (name.contains("\n")) return name.substringBefore("\n") + "..."
    return name
  }
}

fun ChangeSet.toActivityItem(scope: ActivityScope): ActivityItem {
  if (isLabelOnly) return LabelActivityItem(this)
  return ChangeActivityItem(this, scope)
}