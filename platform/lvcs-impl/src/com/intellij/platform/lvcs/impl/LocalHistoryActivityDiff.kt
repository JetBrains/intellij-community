// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.ErrorDiffRequest
import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.revisions.CurrentRevision
import com.intellij.history.core.revisions.Difference
import com.intellij.history.core.revisions.Revision.getDifferencesBetween
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.revertion.DifferenceReverter
import com.intellij.history.integration.revertion.Reverter
import com.intellij.history.integration.revertion.SelectionReverter
import com.intellij.history.integration.ui.models.*
import com.intellij.history.integration.ui.views.DirectoryChange
import com.intellij.history.integration.ui.views.RevisionProcessingProgressAdapter
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.util.containers.JBIterable
import org.jetbrains.annotations.Nls
import java.util.*

/**
 * @see com.intellij.history.integration.ui.models.HistoryDialogModel.getDifferences
 */
internal val RevisionSelection.diff: List<Difference>
  get() = getDifferencesBetween(leftRevision, rightRevision)

/**
 * @see com.intellij.history.integration.ui.models.HistoryDialogModel.createChange
 */
private fun Difference.getChange(gateway: IdeaGateway, scope: ActivityScope): Change {
  if (scope is ActivityScope.Directory) {
    return DirectoryChange(DirectoryChangeModel(this, gateway))
  }
  return Change(getLeftContentRevision(gateway), getRightContentRevision(gateway))
}

internal fun RevisionSelection.getChanges(gateway: IdeaGateway, scope: ActivityScope): Iterable<Change> {
  return JBIterable.from(diff).map { d -> d.getChange(gateway, scope) }
}

private fun ActivityScope.Selection.createSelectionCalculator(gateway: IdeaGateway, selection: RevisionSelection): SelectionCalculator {
  return SelectionCalculator(gateway, listOf(createCurrentRevision(selection)) + selection.allRevisions, from, to)
}

/**
 * @see com.intellij.history.integration.ui.models.HistoryDialogModel.createReverter
 */
internal fun ActivityScope.createReverter(project: Project,
                                          facade: LocalHistoryFacade,
                                          gateway: IdeaGateway,
                                          selection: RevisionSelection): Reverter {
  if (this is ActivityScope.Selection) {
    val calculator = createSelectionCalculator(gateway, selection)
    return SelectionReverter(project, facade, gateway, calculator, selection.leftRevision, selection.rightEntry, from, to)
  }
  return DifferenceReverter(project, facade, gateway, selection.diff, selection.leftRevision)
}

internal fun ActivityScope.diffModel(project: Project?, gateway: IdeaGateway, selection: RevisionSelection): FileDifferenceModel {
  if (this is ActivityScope.Selection) {
    val calculator = createSelectionCalculator(gateway, selection)
    return SelectionDifferenceModel(project, gateway, calculator, selection.leftRevision, selection.rightRevision,
                                    from, to, selection.rightRevision is CurrentRevision)
  }
  return EntireFileDifferenceModel(project, gateway, selection.leftEntry, selection.rightEntry, selection.rightRevision is CurrentRevision)
}

internal class FileActivityDiffRequestProducer(private val scope: ActivityScope,
                                               private val selection: RevisionSelection,
                                               private val diffModel: FileDifferenceModel) : DiffRequestProducer {
  override fun getName(): String = diffModel.title

  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    val progressAdapter = RevisionProcessingProgressAdapter(indicator)
    return FileDifferenceModel.createRequest(diffModel, progressAdapter)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FileActivityDiffRequestProducer) return false
    return scope == other.scope && selection == other.selection
  }

  override fun hashCode() = Objects.hash(scope, selection)
}

private fun fileStatus(leftContentAvailable: Boolean, rightContentAvailable: Boolean): FileStatus {
  if (leftContentAvailable == rightContentAvailable) {
    return FileStatus.MODIFIED
  }
  if (!leftContentAvailable) return FileStatus.ADDED
  return FileStatus.DELETED
}

internal class FileActivityChangeWrapper(project: Project?,
                                         gateway: IdeaGateway,
                                         private val scope: ActivityScope.File,
                                         private val selection: RevisionSelection) : ChangeViewDiffRequestProcessor.Wrapper() {
  private val diffModel = scope.diffModel(project, gateway, selection)

  override fun getFilePath() = scope.filePath
  override fun getFileStatus(): FileStatus {
    val leftContentAvailable = diffModel.isLeftContentAvailable
    val rightContentAvailable = diffModel.isRightContentAvailable
    return fileStatus(leftContentAvailable, rightContentAvailable)
  }

  override fun getUserObject() = scope.file
  override fun getPresentableName() = filePath.name
  override fun createProducer(project: Project?): DiffRequestProducer {
    return FileActivityDiffRequestProducer(scope, selection, diffModel)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FileActivityChangeWrapper) return false
    if (!super.equals(other)) return false

    if (scope != other.scope) return false
    if (selection != other.selection) return false

    return true
  }

  override fun hashCode(): Int = Objects.hash(scope, selection)
}

private class ErrorDiffRequestProducer(val presentableName: @Nls String) : DiffRequestProducer {
  override fun getName(): String = presentableName
  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    return ErrorDiffRequest(DiffBundle.message("error.cant.show.diff.message"))
  }
}

private class DifferenceWrapper(private val gateway: IdeaGateway,
                                private val scope: ActivityScope,
                                private val selection: RevisionSelection,
                                private val difference: Difference,
                                private val targetFilePath: FilePath) : ChangeViewDiffRequestProcessor.Wrapper() {

  constructor(gateway: IdeaGateway, scope: ActivityScope.Directory, selection: RevisionSelection, difference: Difference) :
    this(gateway, scope, selection, difference, difference.filePath ?: scope.filePath)

  override fun getFilePath() = targetFilePath
  override fun getFileStatus(): FileStatus {
    return fileStatus(difference.left != null, difference.right != null)
  }

  override fun getUserObject(): Any = difference
  override fun getPresentableName(): String = targetFilePath.name
  override fun createProducer(project: Project?): DiffRequestProducer {
    val change = difference.getChange(gateway, scope)
    return ChangeDiffRequestProducer.create(project, change) ?: ErrorDiffRequestProducer(presentableName)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DifferenceWrapper) return false
    if (!super.equals(other)) return false

    if (scope != other.scope) return false
    if (selection != other.selection) return false
    if (difference != other.difference) return false

    return true
  }

  override fun hashCode() = Objects.hash(scope, selection, difference)
}

internal data class ActivityDiffDataWithDifferences(val gateway: IdeaGateway,
                                                    val scope: ActivityScope,
                                                    val selection: RevisionSelection,
                                                    val differences: List<Difference>) : ActivityDiffData {
  override fun getPresentableChanges(project: Project): Iterable<ChangeViewDiffRequestProcessor.Wrapper> {
    if (scope is ActivityScope.Directory) {
      return JBIterable.from(differences).filter { it.isFile }.map { DifferenceWrapper(gateway, scope, selection, it) }
    }
    if (scope is ActivityScope.Recent) {
      return JBIterable.from(differences).filter { it.isFile }.map { difference ->
        difference.filePath?.let { DifferenceWrapper(gateway, scope, selection, difference, it) }
      }.filterNotNull()
    }
    return emptyList()
  }
}

internal data class ActivityDiffDataFromModel(val gateway: IdeaGateway,
                                              val scope: ActivityScope.File,
                                              val selection: RevisionSelection) : ActivityDiffData {
  override fun getPresentableChanges(project: Project): Iterable<ChangeViewDiffRequestProcessor.Wrapper> {
    return listOf(FileActivityChangeWrapper(project, gateway, scope, selection))
  }
}