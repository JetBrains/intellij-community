// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class FileAssignmentTracker(
  private val project: Project?,
  private vararg val files: VirtualFile,
) : AssignmentTracker() {
  override fun onEachAssignment() {
    DiffUtil.refreshOnFrameActivation(*files)
  }

  override fun onLastUnassignment() {
    DiffUtil.cleanCachesAfterUse(project, *files)
  }
}
