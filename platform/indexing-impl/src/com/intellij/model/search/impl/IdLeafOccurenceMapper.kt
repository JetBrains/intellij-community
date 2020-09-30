// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.TextOccurrence
import com.intellij.psi.PsiElement

internal object IdLeafOccurenceMapper : LeafOccurrenceMapper<TextOccurrence> {

  override fun mapOccurrence(scope: PsiElement, start: PsiElement, offsetInStart: Int): Collection<TextOccurrence> {
    return listOf(TextOccurrence.of(start, offsetInStart))
  }
}
