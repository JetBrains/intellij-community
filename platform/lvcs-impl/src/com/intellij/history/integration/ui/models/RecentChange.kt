// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.history.integration.ui.models

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.revisions.ChangeRevision
import com.intellij.history.core.revisions.Revision
import com.intellij.history.core.tree.RootEntry
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RecentChange(val revisionBefore: Revision, val revisionAfter: Revision) {
  val changeName: @NlsContexts.Label String?
    get() = revisionAfter.changeSetName

  val timestamp: Long
    get() = revisionAfter.timestamp

  override fun toString(): String {
    return changeName + "[" + DateFormatUtil.formatDateTime(timestamp) + "]"
  }
}

fun LocalHistoryFacade.getRecentChanges(root: RootEntry): List<RecentChange> {
  val result = mutableListOf<RecentChange>()

  for (c in changes) {
    if (c.isContentChangeOnly) continue
    if (c.isLabelOnly) continue
    if (c.name == null) continue

    val before = ChangeRevision(this, root, "", c, true)
    val after = ChangeRevision(this, root, "", c, false)
    result.add(RecentChange(before, after))
    if (result.size >= 20) break
  }

  return result
}