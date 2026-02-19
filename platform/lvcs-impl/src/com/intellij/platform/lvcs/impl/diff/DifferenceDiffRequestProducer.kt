// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.history.core.revisions.Difference
import com.intellij.history.core.tree.Entry
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.ChangeSetActivityItem
import com.intellij.platform.lvcs.impl.ChangeSetSelection
import com.intellij.platform.lvcs.impl.RevisionId
import com.intellij.platform.lvcs.impl.presentableName
import com.intellij.platform.lvcs.impl.revisionId
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.Nls
import java.util.Objects

internal abstract class DifferenceDiffRequestProducer(protected val project: Project?,
                                                      protected val gateway: IdeaGateway,
                                                      protected open val scope: ActivityScope,
                                                      selection: ChangeSetSelection,
                                                      private val isOldContentUsed: Boolean) : DiffRequestProducer {
  protected abstract val difference: Difference

  protected val leftItem = selection.leftItem
  protected val rightItem = selection.rightItem

  override fun getName(): String = scope.presentableName

  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    val leftContent = createContent(difference.left, leftItem.revisionId is RevisionId.Current)
    val rightContent = createContent(difference.right, rightItem.revisionId is RevisionId.Current)

    val leftContentTitle = getTitle(leftItem)
    val rightContentTitle = getTitle(rightItem)

    return SimpleDiffRequest(name, leftContent, rightContent, leftContentTitle, rightContentTitle)
  }

  private fun createContent(entry: Entry?, isCurrent: Boolean): DiffContent {
    if (entry == null) return DiffContentFactory.getInstance().createEmpty()
    if (isCurrent) return runReadAction { createCurrentDiffContent(project, gateway, entry.path) }
    return createDiffContent(project, gateway, entry)
  }

  protected fun getTitle(item: ChangeSetActivityItem?): @Nls String {
    if (item == null) return LocalHistoryBundle.message("current.revision")

    val formattedTimestamp = DateFormatUtil.formatDateTime(item.timestamp)
    if (isOldContentUsed) {
      return LocalHistoryBundle.message("activity.diff.content.title", formattedTimestamp)
    }
    return formattedTimestamp
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DifferenceDiffRequestProducer) return false
    if (!super.equals(other)) return false

    if (scope != other.scope) return false
    if (leftItem != other.leftItem) return false
    if (rightItem != other.rightItem) return false
    if (isOldContentUsed != other.isOldContentUsed) return false

    return true
  }

  override fun hashCode(): Int = Objects.hash(scope, leftItem, rightItem, isOldContentUsed)

  internal class WithDifferenceObject(project: Project?, gateway: IdeaGateway, scope: ActivityScope, selection: ChangeSetSelection,
                                      private val fileDifference: PresentableFileDifference, isOldContentUsed: Boolean)
    : DifferenceDiffRequestProducer(project, gateway, scope, selection, isOldContentUsed), ChangeDiffRequestChain.Producer,
      PresentableChange by fileDifference {

    override val difference: Difference get() = fileDifference.difference

    override fun getName(): String {
      val entry = difference.left ?: difference.right
      if (entry == null) return scope.presentableName
      return FileUtil.toSystemDependentName(entry.path)
    }
  }

  internal open class WithLazyDiff(project: Project?, gateway: IdeaGateway, scope: ActivityScope, selection: ChangeSetSelection,
                                   loadDifference: () -> Difference, isOldContentUsed: Boolean)
    : DifferenceDiffRequestProducer(project, gateway, scope, selection, isOldContentUsed) {
    override val difference by lazy(LazyThreadSafetyMode.PUBLICATION, loadDifference)
  }
}
