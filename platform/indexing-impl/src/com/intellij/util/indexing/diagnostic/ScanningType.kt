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
   * Full project rescan on project open
   */
  FULL_ON_PROJECT_OPEN(true),

  /**
   * Full project rescan on index restart.
   * Index restart happens in two cases:
   * 1. When a language plugin is turned on/off (see FileBasedIndexTumbler)
   * 2. In tests (see usages of FileBasedIndexTumbler.turnOff)
   */
  FULL_ON_INDEX_RESTART(true),

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
   * Partial project rescan on index restart.
   * Index restart happens in two cases:
   * 1. When a language plugin is turned on/off (see FileBasedIndexTumbler)
   * 2. In tests (see usages of FileBasedIndexTumbler.turnOff)
   *
   * The first case (when a language plugin is turned on/off) requires full rescan
   * because we don't know which files need to be indexed, therefore, this type can only appear in tests.
   */
  PARTIAL_ON_INDEX_RESTART(false),

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