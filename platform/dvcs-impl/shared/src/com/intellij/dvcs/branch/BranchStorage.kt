// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.branch

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Tag("branch-storage")
class BranchStorage : BaseState() {
  @get:Property(surroundWithTag = false)
  @get:MapAnnotation(keyAttributeName = "type")
  val branches by map<String, MutableList<DvcsBranchInfo>>()

  fun contains(typeName: String, repositoryRoot: VirtualFile?, branchName: String): Boolean {
    val branches = branches[typeName] ?: return false
    return find(branches, repositoryRoot, branchName) != null
  }

  fun add(typeName: String, repositoryRoot: VirtualFile?, branchName: String) {
    if (contains(typeName, repositoryRoot, branchName)) {
      return
    }

    branches.computeIfAbsent(typeName) { mutableListOf() }.add(DvcsBranchInfo(repositoryRoot.pathOrEmpty(), branchName))
    intIncrementModificationCount()
  }

  fun remove(typeName: String, repositoryRoot: VirtualFile?, branchName: String) {
    val branches = branches[typeName] ?: return
    val toDelete = find(branches, repositoryRoot, branchName) ?: return
    branches.remove(toDelete)
    if (branches.isEmpty()) {
      this.branches.remove(typeName)
    }
    intIncrementModificationCount()
  }

  private fun find(branches: Collection<DvcsBranchInfo>, repositoryRoot: VirtualFile?, sourceBranch: String): DvcsBranchInfo? =
    branches.find { it.sourceName == sourceBranch && repositoryRoot.pathOrEmpty() == it.repoPath }

  private fun VirtualFile?.pathOrEmpty() = this?.path ?: ""
}