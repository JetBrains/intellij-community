// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.diff

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.history.core.revisions.Difference
import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.RevisionSelection
import com.intellij.platform.lvcs.impl.filePath
import java.util.*

internal open class DifferenceWrapper(protected val gateway: IdeaGateway,
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

internal fun fileStatus(leftContentAvailable: Boolean, rightContentAvailable: Boolean): FileStatus {
  if (leftContentAvailable == rightContentAvailable) {
    return FileStatus.MODIFIED
  }
  if (!leftContentAvailable) return FileStatus.ADDED
  return FileStatus.DELETED
}