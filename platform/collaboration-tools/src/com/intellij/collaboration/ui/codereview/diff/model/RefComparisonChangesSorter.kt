// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.model

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.collaboration.util.fileStatus
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun interface RefComparisonChangesSorter {
  fun sort(changes: List<RefComparisonChange>): List<RefComparisonChange>

  object None : RefComparisonChangesSorter {
    override fun sort(changes: List<RefComparisonChange>): List<RefComparisonChange> = changes
  }

  @ApiStatus.Experimental
  class Grouping(private val project: Project, private val groupings: Set<String>) : RefComparisonChangesSorter {
    // TODO: don't build the tree, implement a comparator
    override fun sort(changes: List<RefComparisonChange>): List<RefComparisonChange> {
      val groupingFactory = ChangesGroupingSupport(project, Unit, false).apply {
        setGroupingKeysOrSkip(groupings)
      }.grouping
      val model = TreeModelBuilder(project, groupingFactory).apply {
        for (change in changes) {
          insertChangeNode(change.filePath, myRoot, Node(change))
        }
      }.build()
      return VcsTreeModelData.allUnder(model.root as ChangesBrowserNode<*>).iterateUserObjects(RefComparisonChange::class.java).toList()
    }

    private class Node(change: RefComparisonChange) : AbstractChangesBrowserFilePathNode<RefComparisonChange>(change, change.fileStatus) {
      override fun filePath(userObject: RefComparisonChange): FilePath = userObject.filePath
      override fun originPath(userObject: RefComparisonChange): FilePath? = userObject.filePathBefore
    }
  }
}
