// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration.revertion

import com.intellij.history.core.revisions.Revision
import com.intellij.history.core.tree.Entry
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.ui.models.Progress
import com.intellij.history.integration.ui.models.SelectionCalculator
import com.intellij.history.integration.ui.models.toRevisionId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lvcs.impl.RevisionId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SelectionReverter(
  project: Project,
  gateway: IdeaGateway,
  private val calculator: SelectionCalculator,
  private val targetRevisionId: RevisionId,
  private val targetPath: @NlsSafe String,
  private val fromLine: Int,
  private val toLine: Int,
  commandName: () -> String,
) : Reverter(project, gateway, commandName) {
  constructor(
    project: Project,
    gateway: IdeaGateway,
    calculator: SelectionCalculator,
    targetRevision: Revision,
    rightEntry: Entry,
    fromLine: Int,
    toLine: Int,
  ) : this(project,
           gateway,
           calculator,
           targetRevision.toRevisionId(),
           rightEntry.getPath(),
           fromLine,
           toLine,
           { getRevertCommandName(targetRevision) })

  override val filesToClearROStatus: List<VirtualFile>
    get() = listOfNotNull(gateway.findVirtualFile(targetPath))

  override fun doRevert() {
    val b = calculator.getSelectionFor(targetRevisionId, Progress.EMPTY)

    val d = gateway.getDocument(targetPath)!!

    val from = d.getLineStartOffset(fromLine)
    val to = d.getLineEndOffset(toLine)

    d.replaceString(from, to, b.blockContent)
  }
}
