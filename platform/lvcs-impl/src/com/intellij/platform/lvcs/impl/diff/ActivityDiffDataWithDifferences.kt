// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.diff

import com.intellij.history.core.revisions.Difference
import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.platform.lvcs.impl.ActivityDiffData
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.RevisionSelection
import com.intellij.platform.lvcs.impl.isCurrent
import com.intellij.util.containers.JBIterable

private data class ActivityDiffDataWithDifferences(val gateway: IdeaGateway,
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

internal fun createDiffData(gateway: IdeaGateway, scope: ActivityScope, revisionSelection: RevisionSelection): ActivityDiffData {
  val differences = if (scope is ActivityScope.SingleFile || scope is ActivityScope.Selection) {
    listOf(Difference(revisionSelection.leftEntry, revisionSelection.rightEntry, revisionSelection.rightRevision.isCurrent))
  }
  else {
    revisionSelection.diff
  }
  return ActivityDiffDataWithDifferences(gateway, scope, revisionSelection, differences)
}