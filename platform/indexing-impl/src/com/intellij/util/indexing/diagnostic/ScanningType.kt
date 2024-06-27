// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import org.jetbrains.annotations.ApiStatus.Internal


/**
 * isFull - if the whole project was rescanned (instead of a part of it)
 */
@Internal
enum class ScanningType(val isFull: Boolean) {
  /**
   * Full project rescan forced by user via Repair IDE action
   */
  FULL_FORCED(true),

  /**
   * It's mandatory full project rescan on project open
   */
  FULL_ON_PROJECT_OPEN(true),

  /**
   * Full project rescan requested by some code
   */
  FULL(true),


  /**
   * Partial rescan forced by user via Repair IDE action on a limited scope (not full project)
   */
  PARTIAL_FORCED(false),

  /**
   * Full scanning on project open was skipped, and only dirty files from the last IDE session are scanned
   */
  PARTIAL_ON_PROJECT_OPEN(false),

  /**
   * Partial project rescan requested by some code
   */
  PARTIAL(false),

  /**
   * Some files were considered changed and therefore rescanned
   */
  REFRESH(false);

  companion object {
    fun merge(first: ScanningType, second: ScanningType): ScanningType = returnFirstFound(first, second)

    private fun returnFirstFound(first: ScanningType, second: ScanningType): ScanningType {
      val types = listOf(FULL_FORCED, FULL_ON_PROJECT_OPEN, FULL, PARTIAL_FORCED, PARTIAL_ON_PROJECT_OPEN, PARTIAL, REFRESH)
      for (type in types) {
        if (first == type || second == type) return type
      }
      throw IllegalStateException("Unexpected ScanningType $first $second")
    }
  }
}