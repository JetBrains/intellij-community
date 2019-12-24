// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementsAtOffsetUpTo

internal data class LeafOccurrence(val scope: PsiElement, val start: PsiElement, val offsetInStart: Int)

internal fun LeafOccurrence.elementsUp(): Iterator<Pair<PsiElement, Int>> = start.elementsAtOffsetUpTo(offsetInStart, scope)

internal typealias OccurrenceProcessor = (occurrence: LeafOccurrence) -> Boolean

/**
 * @return [OccurrenceProcessor] which runs bunch of [OccurrenceProcessor]s one by one
 */
internal fun Collection<OccurrenceProcessor>.compound(progress: ProgressIndicator): OccurrenceProcessor {
  singleOrNull()?.let {
    return it
  }
  return { occurrence: LeafOccurrence ->
    this@compound.runProcessors(progress, occurrence)
  }
}

/**
 * @return runs bunch of [OccurrenceProcessor]s one by one
 */
internal fun Collection<OccurrenceProcessor>.runProcessors(progress: ProgressIndicator, occurrence: LeafOccurrence): Boolean {
  for (processor in this) {
    progress.checkCanceled()
    if (!processor(occurrence)) {
      return false
    }
  }
  return true
}

internal fun processOffsets(scope: PsiElement,
                            offsetsInScope: IntArray,
                            patternLength: Int,
                            progress: ProgressIndicator,
                            processor: OccurrenceProcessor): Boolean {
  if (offsetsInScope.isEmpty()) {
    return true
  }
  val scopeNode = requireNotNull(scope.node) {
    "Scope doesn't have node, can't scan: $scope; containingFile: ${scope.containingFile}"
  }
  return LowLevelSearchUtil.processOffsets(scopeNode, offsetsInScope, progress) { node, offsetInNode ->
    processOffset(scopeNode, node, offsetInNode, patternLength, processor)
  }
}

private fun processOffset(scopeNode: ASTNode,
                          node: ASTNode,
                          offsetInNode: Int,
                          patternLength: Int,
                          processor: OccurrenceProcessor): Boolean {
  var currentNode = node
  var currentOffset = offsetInNode
  while (currentNode !== scopeNode) {
    if (currentNode.textLength >= currentOffset + patternLength) {
      return processor(LeafOccurrence(scopeNode.psi, currentNode.psi, currentOffset))
    }
    currentOffset += currentNode.startOffsetInParent
    currentNode = currentNode.treeParent ?: return true
  }
  return true
}
