// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.ui.models

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.RevisionsCollector
import com.intellij.history.core.revisions.Revision
import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

data class RevisionData(val currentRevision: Revision, val revisions: List<RevisionItem>)

val RevisionData.allRevisions get() = listOf(currentRevision) + revisions.map { it.revision }

internal fun collectRevisionData(project: Project,
                                 gateway: IdeaGateway,
                                 facade: LocalHistoryFacade,
                                 file: VirtualFile,
                                 filter: String? = null): RevisionData {
  return runReadAction {
    gateway.registerUnsavedDocuments(facade)

    val path = gateway.getPathOrUrl(file)
    val root = gateway.createTransientRootEntry()
    val all = RevisionsCollector(facade, root, path, project.getLocationHash(), filter).result

    RevisionData(all[0], mergeLabelsWithRevisions(all.subList(1, all.size)))
  }
}

private fun mergeLabelsWithRevisions(revisions: List<Revision>): List<RevisionItem> {
  val result = mutableListOf<RevisionItem>()

  for (revision in revisions.asReversed()) {
    if (revision.isLabel && !result.isEmpty()) {
      result.last().labels.addFirst(revision)
    }
    else {
      result.add(RevisionItem(revision))
    }
  }

  return result.asReversed()
}