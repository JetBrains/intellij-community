// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl

import com.intellij.diff.contents.DiffContent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Hady helper to implement the [com.intellij.diff.requests.DiffRequest.onAssigned] and [com.intellij.diff.contents.DiffContent.onAssigned]
 */
open class AssignmentTracker {
  companion object {
    private val logger = logger<AssignmentTracker>()

    @JvmStatic
    fun onContentsAssigned(contents: List<DiffContent>, isAssigned: Boolean) {
      try {
        for (content in contents) {
          content.onAssigned(isAssigned)
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
        if (assignments == 1) {
          onFirstAssignment()
        }

        onEachAssignment()
      }
      else {
        onEachUnassignment()

        if (assignments == 0) {
          onLastUnassignment()
        }
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
