// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.api

import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.psi.search.SearchScope
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * The thing being renamed.
 *
 * May be based on a PsiElement, Symbol or whatever.
 * Visible in the UI.
 * Lifecycle: single read action.
 *
 * @see com.intellij.find.usages.api.SearchTarget
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
  val maximalSearchScope: SearchScope?
    get() = null

  /**
   * @return presentation to be displayed in the disambiguation popup
   * when several [different][equals] targets exist to choose from,
   * or in the Usage View (only [icon][TargetPresentation.icon]
   * and [presentable text][TargetPresentation.presentableText] are used)
   * @see com.intellij.find.usages.api.SearchTarget.presentation
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun presentation(): TargetPresentation

  /**
   * The single [RenameTarget] might be referenced differently in different contexts,
   * e.g. we search for short name of a Java class in strings and comments, and fully qualified name in plain text.
   */
  fun textTargets(context: ReplaceTextTargetContext): Collection<ReplaceTextTarget> = emptyList()
}
