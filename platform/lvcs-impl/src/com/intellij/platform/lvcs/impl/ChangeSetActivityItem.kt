// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.platform.lvcs.impl

import com.intellij.history.ActivityId
import com.intellij.history.core.Paths
import com.intellij.history.core.changes.*
import com.intellij.history.integration.CommonActivity
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class ChangeSetActivityItem(changeSet: ChangeSet) : ActivityItem {
  override val timestamp: Long = changeSet.timestamp
  val id = changeSet.id
  val activityId: ActivityId? = changeSet.activityId
  open val fullName: @NlsSafe String? get() = name
  abstract val name: @NlsSafe String?

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChangeSetActivityItem) return false
    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String {
    val fullName = fullName.orEmpty()
    return DateFormatUtil.formatDateTime(timestamp) +
           if (fullName.isNotBlank()) ": $fullName" else ""
  }
}

val ChangeSetActivityItem?.revisionId: RevisionId get() = if (this != null) RevisionId.ChangeSet(id) else RevisionId.Current

internal class ChangeActivityItem(changeSet: ChangeSet, scope: ActivityScope) : ChangeSetActivityItem(changeSet) {
  override val name = getName(changeSet, scope)

  private fun getName(changeSet: ChangeSet, scope: ActivityScope): @NlsContexts.Label String? {
    if (changeSet.activityId == CommonActivity.ExternalChange || changeSet.name == null) {
      val nameFromSingleChange = getNameFromSingleChange(changeSet, scope)
      if (nameFromSingleChange != null) return nameFromSingleChange
    }
    if (changeSet.name != null) return changeSet.name
    if (!scope.hasMultipleFiles) return LocalHistoryBundle.message("activity.item.presentation")
    return changeSet.presentableNameFromPaths()
  }

  private fun getNameFromSingleChange(changeSet: ChangeSet, scope: ActivityScope): @NlsContexts.Label String? {
    val singleChange = changeSet.changes.singleOrNull() ?: return null
    return when (singleChange) {
      is CreateEntryChange -> LocalHistoryBundle.message("activity.item.presentation.create.path", Paths.getNameOf(singleChange.path))
      is DeleteChange -> LocalHistoryBundle.message("activity.item.presentation.delete.path", Paths.getNameOf(singleChange.path))
      is RenameChange -> LocalHistoryBundle.message("activity.item.presentation.rename.path", singleChange.oldName, Paths.getNameOf(singleChange.path))
      is MoveChange -> LocalHistoryBundle.message("activity.item.presentation.move.path", Paths.getNameOf(singleChange.path),
                                                  Paths.getNameOf(Paths.getParentOf(singleChange.path)))
      is ContentChange ->
        if (scope.hasMultipleFiles) LocalHistoryBundle.message("activity.item.presentation.modify.path", Paths.getNameOf(singleChange.path))
        else null
      else -> null
    }
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
  override val fullName = changeSet.label ?: changeSet.name
  override val name get() = shorten(fullName)
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