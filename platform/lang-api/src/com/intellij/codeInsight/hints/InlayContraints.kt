// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.RootInlayPresentation

/**
 * Presentation with constraints to the place where it should be placed
 */
interface ConstrainedPresentation<Content : Any, Constraints: Any> {
  val root: RootInlayPresentation<Content>

  /**
   * priority in list during update
   */
  val priority: Int

  val constraints: Constraints?
}

class HorizontalConstraints(
  val priority: Int,
  val relatesToPrecedingText: Boolean // specific to placement, but not actually possible to handle in case of multiple hints
)

data class HorizontalConstrainedPresentation<Content : Any>(
  override val root: RootInlayPresentation<Content>,
  override val constraints: HorizontalConstraints?
) : ConstrainedPresentation<Content, HorizontalConstraints> {
  override val priority: Int
  get() = constraints?.priority ?: 0
}

class BlockConstraints(
  val relatesToPrecedingText: Boolean,
  val priority: Int
)


data class BlockConstrainedPresentation<T : Any>(
  override val root: RootInlayPresentation<T>,
  override val constraints: BlockConstraints?
)  : ConstrainedPresentation<T, BlockConstraints>{
  override val priority: Int
    get() = constraints?.priority ?: 0
}