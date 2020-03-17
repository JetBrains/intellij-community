// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.TextOccurrence
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementsAtOffsetUpTo

internal object TextOccurrenceWalker : LeafOccurrenceMapper<TextOccurrence> {

  /**
   * Walks up the tree for each element and offset
   * generating sequence of [TextOccurrence]s for each tree level per offset
   * starting from the [start] and ending with [scope].
   */
  override fun mapOccurrence(scope: PsiElement, start: PsiElement, offsetInStart: Int): Collection<TextOccurrence> {
    return start.elementsAtOffsetUpTo(offsetInStart, scope)
      .asSequence()
      .map { (currentElement, currentOffset) -> TextOccurrence.of(currentElement, currentOffset) }
      .toList()
  }
}
