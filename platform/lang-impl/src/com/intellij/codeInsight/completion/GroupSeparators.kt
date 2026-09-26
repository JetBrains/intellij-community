// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GroupSeparators")

package com.intellij.codeInsight.completion

import com.intellij.codeInsight.completion.group.CompletionGroup
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class GroupSeparator(
  val group: CompletionGroup,
  val insertionIndex: Int,
)

/**
 * Computes top-down `(insertionIndex, group)` positions for the separators to add to [model].
 *
 * The model must satisfy two invariants:
 *  - every grouped element forms a contiguous suffix of the model;
 *  - within that suffix, each group occupies a single contiguous run.
 *
 * @return `null` when grouping should be skipped — either there is nothing to do
 * (groups disabled, no cached group items, empty model, untrailed model) or an invariant is violated.
 */
@ApiStatus.Internal
fun computeGroupSeparators(
  model: List<LookupElement>,
  supportGroups: Boolean,
  hasGroups: Boolean,
): List<GroupSeparator>? {
  if (!supportGroups || !hasGroups || model.isEmpty()) return null

  var currentGroup = CompletionGroup.get(model.last()) ?: return null

  val separators = mutableListOf<GroupSeparator>()
  val visitedGroups = mutableSetOf<CompletionGroup>()

  // Where the topmost group opens; stays 0 when the entire model is grouped (loop exhausts without finding a boundary).
  var topBoundary = 0
  for (i in model.size - 2 downTo 0) {
    val group = CompletionGroup.get(model[i])

    if (group == null) {
      topBoundary = i + 1
      break
    }

    if (group == currentGroup) continue

    // Switching to a group we've already moved on from means the group is split.
    if (!visitedGroups.add(group)) return null

    separators.add(GroupSeparator(currentGroup, i + 1))
    currentGroup = group
  }

  // Crossed from the grouped suffix into the ungrouped head — make sure nothing grouped sneaks back in above.
  // `topBoundary - 1` is already checked, so starting from `-2`
  for (j in topBoundary - 2 downTo 0) {
    if (CompletionGroup.get(model[j]) != null) return null
  }

  separators.add(GroupSeparator(currentGroup, topBoundary))
  separators.reverse()
  return separators
}

@ApiStatus.Internal
fun insertGroupSeparators(
  model: MutableList<LookupElement>,
  separators: List<GroupSeparator>,
  separatorFactory: (CompletionGroup) -> LookupElement,
) {
  for (i in separators.indices) {
    val entry = separators[i]
    // Each previously inserted separator shifts subsequent target indices by one.
    model.add(entry.insertionIndex + i, separatorFactory(entry.group))
  }
}
