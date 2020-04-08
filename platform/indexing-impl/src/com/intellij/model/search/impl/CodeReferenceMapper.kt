// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.search.CodeReferenceSearcher
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.psi.PsiElement

class CodeReferenceMapper(
  private val pointer: Pointer<out Symbol>,
  private val searcher: CodeReferenceSearcher
) : LeafOccurrenceMapper<PsiSymbolReference> {

  override fun mapOccurrence(scope: PsiElement, start: PsiElement, offsetInStart: Int): Collection<PsiSymbolReference> {
    val target = pointer.dereference() ?: return emptyList()
    return searcher.getReferences(target, scope, start, offsetInStart)
  }
}
