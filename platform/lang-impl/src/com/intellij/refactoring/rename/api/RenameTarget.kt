// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.api

import com.intellij.model.Pointer
import com.intellij.navigation.TargetPopupPresentation
import com.intellij.psi.search.SearchScope

/**
 * The thing being renamed.
 *
 * May be based on a PsiElement, Symbol or whatever.
 * Visible in the UI.
 * Lifecycle: single read action.
 *
 * @see com.intellij.refactoring.rename.symbol.SymbolRenameTarget
 * @see com.intellij.find.usages.SearchTarget
 */
interface RenameTarget {

  /**
   * @return smart pointer used to restore the [RenameTarget] instance in the subsequent read actions
   */
  fun createPointer(): Pointer<out RenameTarget>

  /**
   * Name of the target before the refactoring.
   */
  val targetName: String

  /**
   * Returns maximal search scope where usages to this target might exist, or `null` to search everywhere.
   * Returning [com.intellij.psi.search.LocalSearchScope] will also make search scope unavailable to change in the UI.
   */
  @JvmDefault
  val maximalSearchScope: SearchScope?
    get() = null

  /**
   * @return presentation to be displayed in the disambiguation popup
   * when several [different][equals] targets exist to choose from,
   * or in the Usage View (only [icon][TargetPopupPresentation.getIcon]
   * and [presentable text][TargetPopupPresentation.getPresentableText] are used)
   * @see com.intellij.find.usages.SearchTarget.presentation
   */
  val presentation: TargetPopupPresentation

  /**
   * The single [RenameTarget] might be referenced differently in different contexts,
   * e.g. we search for short name of a Java class in strings and comments, and fully qualified name in plain text.
   */
  @JvmDefault
  fun textTargets(context: ReplaceTextTargetContext): Collection<ReplaceTextTarget> = emptyList()
}
