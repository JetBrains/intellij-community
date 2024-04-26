// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.diff

import com.intellij.history.core.revisions.Difference
import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.platform.lvcs.impl.ActivityDiffObject
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.ChangeSetSelection
import java.util.*

internal class DifferenceObject(private val gateway: IdeaGateway,
                                private val scope: ActivityScope,
                                private val selection: ChangeSetSelection,
                                internal val difference: Difference,
                                private val targetFilePath: FilePath,
                                private val isOldContentUsed: Boolean) : ActivityDiffObject {

  override fun getFilePath() = targetFilePath
  override fun getFileStatus(): FileStatus {
    return fileStatus(difference.left != null, difference.right != null)
  }

  override fun createProducer(project: Project?): ChangeDiffRequestChain.Producer {
    return DifferenceDiffRequestProducer.WithDifferenceObject(project, gateway, scope, selection, this, isOldContentUsed)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DifferenceObject) return false
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