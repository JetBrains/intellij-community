// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface ChangesSelection {
  val changes: List<RefComparisonChange>
  val selectedIdx: Int

  /**
   * Single change selected from [changes]
   */
  data class Precise(override val changes: List<RefComparisonChange>,
                     override val selectedIdx: Int = 0,
                     val location: DiffLineLocation? = null)
    : ChangesSelection {
    constructor(changes: List<RefComparisonChange>, change: RefComparisonChange, location: DiffLineLocation? = null)
      : this(changes, changes.indexOfFirst { it === change }, location)
  }

  /**
   * Changes selected by a certain group (like directory)
   */
  data class Fuzzy(override val changes: List<RefComparisonChange>,
                   override val selectedIdx: Int = 0) : ChangesSelection
}

val ChangesSelection.selectedChange: RefComparisonChange?
  get() = selectedIdx.let { changes.getOrNull(it) }

fun ChangesSelection.Precise.withLocation(location: DiffLineLocation): ChangesSelection.Precise =
  ChangesSelection.Precise(changes, selectedIdx, location)

fun ChangesSelection.copyWithSelection(change: RefComparisonChange): ChangesSelection =
  when (this) {
    is ChangesSelection.Fuzzy -> copy(selectedIdx = changes.indexOfFirst { it == change })
    is ChangesSelection.Precise -> copy(selectedIdx = changes.indexOfFirst { it == change })
  }

@ApiStatus.Experimental
fun ChangesSelection?.equalChanges(other: Any?): Boolean {
  if (this == null && other != null) return false
  if (this != null && other == null) return false
  if (other === this) return true
  if (this == null || other == null) return false // for null safety

  other as ChangesSelection

  if (changes != other.changes) return false
  if (selectedIdx != other.selectedIdx) return false
  return true
}