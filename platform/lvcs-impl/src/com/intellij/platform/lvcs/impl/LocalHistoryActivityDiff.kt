// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.revisions.Difference
import com.intellij.history.core.revisions.Revision
import com.intellij.history.core.revisions.Revision.getDifferencesBetween
import com.intellij.history.core.tree.Entry
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.history.integration.revertion.DifferenceReverter
import com.intellij.history.integration.revertion.Reverter
import com.intellij.history.integration.revertion.SelectionReverter
import com.intellij.history.integration.ui.models.DirectoryChangeModel
import com.intellij.history.integration.ui.models.RevisionProcessingProgress
import com.intellij.history.integration.ui.models.SelectionCalculator
import com.intellij.history.integration.ui.views.DirectoryChange
import com.intellij.history.integration.ui.views.RevisionProcessingProgressAdapter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.util.containers.JBIterable
import com.intellij.util.text.DateFormatUtil
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

private fun fileStatus(leftContentAvailable: Boolean, rightContentAvailable: Boolean): FileStatus {
  if (leftContentAvailable == rightContentAvailable) {
    return FileStatus.MODIFIED
  }
  if (!leftContentAvailable) return FileStatus.ADDED
  return FileStatus.DELETED
}

internal open class DifferenceDiffRequestProducer(protected val project: Project?,
                                                  protected val gateway: IdeaGateway,
                                                  protected open val scope: ActivityScope,
                                                  protected val selection: RevisionSelection,
                                                  protected val difference: Difference) : DiffRequestProducer {
  override fun getName(): String {
    val entry = difference.left ?: difference.right
    if (entry == null) return scope.presentableName
    return FileUtil.toSystemDependentName(entry.path)
  }

  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    val leftContent = createContent(difference.left, selection.leftRevision.isCurrent)
    val rightContent = createContent(difference.right, selection.rightRevision.isCurrent)

    val leftContentTitle = getTitle(selection.leftRevision)
    val rightContentTitle = getTitle(selection.rightRevision)

    return SimpleDiffRequest(name, leftContent, rightContent, leftContentTitle, rightContentTitle)
  }

  private fun createContent(entry: Entry?, isCurrent: Boolean): DiffContent {
    if (entry == null) return DiffContentFactory.getInstance().createEmpty()
    if (isCurrent) return runReadAction { createCurrentDiffContent(project, gateway, entry.path) }
    return createDiffContent(project, gateway, entry)
  }

  protected fun getTitle(revision: Revision): @Nls String {
    if (revision.isCurrent) return LocalHistoryBundle.message("current.revision")

    val formattedTimestamp = DateFormatUtil.formatDateTime(revision.timestamp)
    if (revision.isOldContentUsed && !revision.isLabel) {
      return LocalHistoryBundle.message("activity.diff.content.title", formattedTimestamp)
    }
    return formattedTimestamp
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DifferenceDiffRequestProducer) return false
    if (!super.equals(other)) return false

    if (scope != other.scope) return false
    if (selection != other.selection) return false
    if (difference != other.difference) return false

    return true
  }

  override fun hashCode(): Int = Objects.hash(scope, selection, difference)
}

private open class DifferenceWrapper(protected val gateway: IdeaGateway,
                                     protected open val scope: ActivityScope,
                                     protected val selection: RevisionSelection,
                                     protected val difference: Difference,
                                     private val targetFilePath: FilePath) : ChangeViewDiffRequestProcessor.Wrapper() {

  constructor(gateway: IdeaGateway, scope: ActivityScope.File, selection: RevisionSelection, difference: Difference) :
    this(gateway, scope, selection, difference, difference.filePath ?: scope.filePath)

  override fun getFilePath() = targetFilePath
  override fun getFileStatus(): FileStatus {
    return fileStatus(difference.left != null, difference.right != null)
  }

  override fun getUserObject(): Any = difference
  override fun getPresentableName(): String = targetFilePath.name
  override fun createProducer(project: Project?): DiffRequestProducer {
    return DifferenceDiffRequestProducer(project, gateway, scope, selection, difference)
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

private class SelectionDiffRequestProducer(project: Project?,
                                           gateway: IdeaGateway,
                                           override val scope: ActivityScope.Selection,
                                           selection: RevisionSelection,
                                           difference: Difference,
                                           private val selectionCalculator: SelectionCalculator)
  : DifferenceDiffRequestProducer(project, gateway, scope, selection, difference) {

  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    val leftContent = createContent(difference.left, selection.leftRevision, selection.leftRevision.isCurrent, indicator)
    val rightContent = createContent(difference.right, selection.rightRevision, selection.rightRevision.isCurrent, indicator)

    val leftContentTitle = getTitle(selection.leftRevision)
    val rightContentTitle = getTitle(selection.rightRevision)

    return SimpleDiffRequest(name, leftContent, rightContent, leftContentTitle, rightContentTitle)
  }

  private fun createContent(entry: Entry?, revision: Revision, isCurrent: Boolean, indicator: ProgressIndicator): DiffContent {
    if (entry == null) return DiffContentFactory.getInstance().createEmpty()
    if (isCurrent) return createCurrentDiffContent(project, gateway, entry.path, scope.from, scope.to)
    return createDiffContent(gateway, entry, revision, selectionCalculator, RevisionProcessingProgressAdapter(indicator))
  }
}

private class SelectionDifferenceWrapper(gateway: IdeaGateway, override val scope: ActivityScope.Selection, selection: RevisionSelection,
                                         difference: Difference, private val selectionCalculator: SelectionCalculator) :
  DifferenceWrapper(gateway, scope, selection, difference) {
  override fun getFileStatus(): FileStatus {
    val isLeftContentAvailable = difference.left != null && selectionCalculator.canCalculateFor(selection.leftRevision, RevisionProcessingProgress.EMPTY)
    val isRightContentAvailable = difference.right != null && selectionCalculator.canCalculateFor(selection.rightRevision, RevisionProcessingProgress.EMPTY)
    return fileStatus(isLeftContentAvailable, isRightContentAvailable)
  }

  override fun createProducer(project: Project?): DiffRequestProducer {
    return SelectionDiffRequestProducer(project, gateway, scope, selection, difference, selectionCalculator)
  }
}

internal data class ActivityDiffDataWithDifferences(val gateway: IdeaGateway,
                                                    val scope: ActivityScope,
                                                    val selection: RevisionSelection,
                                                    val differences: List<Difference>) : ActivityDiffData {
  override fun getPresentableChanges(project: Project): Iterable<ChangeViewDiffRequestProcessor.Wrapper> {
    val fileDifferences = JBIterable.from(differences).filter { it.isFile }
    return when (scope) {
      is ActivityScope.Selection -> {
        val calculator = scope.createSelectionCalculator(gateway, selection)
        fileDifferences.map { SelectionDifferenceWrapper(gateway, scope, selection, it, calculator) }
      }
      is ActivityScope.File -> {
        fileDifferences.map { DifferenceWrapper(gateway, scope, selection, it) }
      }
      ActivityScope.Recent -> {
        fileDifferences.map { difference ->
          difference.filePath?.let { DifferenceWrapper(gateway, scope, selection, difference, it) }
        }.filterNotNull()
      }
    }
  }
}