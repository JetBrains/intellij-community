// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Hady helper to implement the [com.intellij.diff.requests.DiffRequest.onAssigned] and [com.intellij.diff.contents.DiffContent.onAssigned]
 */
open class AssignmentTracker {
  companion object {
    private val logger = logger<AssignmentTracker>()
  }

  private var assignments: Int = 0

  @RequiresEdt
  fun onAssigned(isAssigned: Boolean) {
    if (isAssigned) {
      assignments++
    }
    else {
      assignments--
    }
    logger.assertTrue(assignments >= 0)

    try {
      if (isAssigned) {
        onEachAssignment()

        if (assignments == 1) {
          onFirstAssignment()
        }
      }
      else {
        onEachUnassignment()

        if (assignments == 0) {
          onLastUnassignment()
        }
        assignments--
      }
    }
    catch (e: Throwable) {
      val err = when {
        Logger.shouldRethrow(e) -> RuntimeException(e)
        else -> e
      }
      logger.error(err)
    }
  }

  open fun onEachAssignment() {}
  open fun onEachUnassignment() {}

  open fun onFirstAssignment() {}
  open fun onLastUnassignment() {}
}

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
