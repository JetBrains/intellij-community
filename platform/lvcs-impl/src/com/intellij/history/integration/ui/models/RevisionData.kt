// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.ui.models

import com.intellij.history.core.HistoryPathFilter
import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.RevisionsCollector
import com.intellij.history.core.processContents
import com.intellij.history.core.revisions.CurrentRevision
import com.intellij.history.core.revisions.Revision
import com.intellij.history.core.tree.RootEntry
import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lvcs.impl.RevisionId
import com.intellij.util.PairProcessor

data class RevisionData(val currentRevision: Revision, val revisions: List<RevisionItem>)

val RevisionData.allRevisions get() = listOf(currentRevision) + revisions.map { it.revision }

internal fun Revision.toRevisionId() = if (changeSetId == null) RevisionId.Current else RevisionId.ChangeSet(changeSetId!!)

internal fun collectRevisionData(project: Project,
                                 gateway: IdeaGateway,
                                 facade: LocalHistoryFacade,
                                 root: RootEntry,
                                 file: VirtualFile,
                                 filter: String? = null,
                                 before: Boolean = true): RevisionData {
  gateway.registerUnsavedDocuments(facade)
  val path = gateway.getPathOrUrl(file)
  val revisionItems = mergeLabelsWithRevisions(RevisionsCollector.collect(facade, root, path, project.getLocationHash(), HistoryPathFilter.create(filter, project), before))
  return RevisionData(CurrentRevision(root, path), revisionItems)
}

private fun mergeLabelsWithRevisions(revisions: List<Revision>): List<RevisionItem> {
  val result = mutableListOf<RevisionItem>()

  for (revision in revisions.asReversed()) {
    if (revision.isLabel) {
      if (!result.isEmpty()) {
        result.last().labels.addFirst(revision)
      }
    }
    else {
      result.add(RevisionItem(revision))
    }
  }

  return result.asReversed()
}

fun LocalHistoryFacade.filterContents(gateway: IdeaGateway, file: VirtualFile, revisions: List<Revision>, filter: String,
                                      before: Boolean): Set<Long> {
  val result = mutableSetOf<Long>()
  processContents(gateway, file, revisions, before) { revision, content ->
    if (Thread.currentThread().isInterrupted) return@processContents false
    if (content?.contains(filter, true) == true) {
      val id = revision.changeSetId
      if (id != null) result.add(id)
    }
    true
  }
  return result
}

fun filterContents(selectionCalculator: SelectionCalculator, filter: String): MutableSet<Long> {
  val result = mutableSetOf<Long>()
  selectionCalculator.processContents { id, contents ->
    if (Thread.currentThread().isInterrupted) return@processContents false
    if (contents.contains(filter, true)) result.add(id)
    true
  }
  return result
}

internal fun LocalHistoryFacade.processContents(gateway: IdeaGateway, file: VirtualFile, revisions: List<Revision>, before: Boolean,
                                                processor: PairProcessor<in Revision, in String?>) {
  val revisionMap = revisions.filter { !it.isLabel }.associateBy { it.changeSetId }
  if (revisionMap.isEmpty()) return

  val root = revisionMap.values.first().root.copy()
  val path = gateway.getPathOrUrl(file)

  val currentRevision = revisionMap[null]
  if (currentRevision != null) {
    val entry = root.findEntry(path)
    processor.process(currentRevision, entry?.content?.getString(entry, gateway))
  }

  processContents(gateway, root, path, revisionMap.keys.filterNotNullTo(mutableSetOf()), before) { changeSetId, content ->
    val revision = revisionMap[changeSetId]
    revision == null || processor.process(revision, content)
  }
}