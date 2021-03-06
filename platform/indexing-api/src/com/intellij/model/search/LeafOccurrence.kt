// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus.NonExtendable

@NonExtendable
interface LeafOccurrence {

  /**
   * The top-most element with the occurrence.
   *
   * In case the search is conducted in [LocalSearchScope][com.intellij.psi.search.LocalSearchScope],
   * this would be one of [scope elements][com.intellij.psi.search.LocalSearchScope.getScope],
   * in other cases the [scope] is a containing file of [start].
   */
  val scope: PsiElement

  /**
   * The bottom-most element containing the whole occurrence, usually a leaf element.
   */
  val start: PsiElement

  /**
   * Start offset of the occurrence in [start].
   */
  val offsetInStart: Int

  operator fun component1(): PsiElement = scope

  operator fun component2(): PsiElement = start

  operator fun component3(): Int = offsetInStart
}
