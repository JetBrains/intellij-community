// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.branch

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.components.BaseState
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

  fun contains(typeName: String, repository: Repository?, branchName: String): Boolean {
    val branches = branches[typeName] ?: return false
    return DvcsBranchUtil.find(branches, repository, branchName) != null
  }

  fun add(typeName: String, repository: Repository?, branchName: String) {
    if (contains(typeName, repository, branchName)) {
      return
    }

    branches.computeIfAbsent(typeName) { mutableListOf() }.add(DvcsBranchInfo(DvcsBranchUtil.getPathFor(repository), branchName))
    incrementModificationCount()
  }

  fun remove(typeName: String, repository: Repository?, branchName: String) {
    val branches = branches[typeName] ?: return
    val toDelete = DvcsBranchUtil.find(branches, repository, branchName) ?: return
    branches.remove(toDelete)
    if (branches.isEmpty()) {
      this.branches.remove(typeName)
    }
    incrementModificationCount()
  }
}