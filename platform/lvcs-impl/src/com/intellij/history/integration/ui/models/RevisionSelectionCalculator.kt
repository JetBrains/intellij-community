// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.ui.models

import com.intellij.history.core.revisions.Revision
import com.intellij.history.core.tree.Entry
import com.intellij.history.integration.IdeaGateway
import com.intellij.platform.lvcs.impl.RevisionId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RevisionSelectionCalculator(gateway: IdeaGateway, revisions: List<Revision>, fromLine: Int, toLine: Int) :
  SelectionCalculator(gateway, revisions.map { it.toRevisionId() }, fromLine, toLine) {

  private val idToRevision = revisions.associateBy { it.toRevisionId() }

  fun canCalculateFor(revision: Revision, progress: Progress): Boolean {
    return canCalculateFor(revision.toRevisionId(), progress)
  }

  override fun getEntry(revision: RevisionId): Entry? {
    return idToRevision[revision]?.findEntry()
  }
}