// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.list

import com.intellij.ui.speedSearch.SearchAwareRenderer

/**
 * A renderer which combines [two renderers][LeftRightRenderer] and combines [search string][getItemSearchString] from both renderers.
 */
class LeftRightSearchAwareRenderer<T>(
  override val mainRenderer: SearchAwareRenderer<T>,
  override val rightRenderer: SearchAwareRenderer<T>
) : LeftRightRenderer<T>(),
    SearchAwareRenderer<T> {

  override fun getItemSearchString(item: T): String? {
    val main = mainRenderer.getItemSearchString(item)
    val right = rightRenderer.getItemSearchString(item)
    return when {
      main == null -> right
      right == null -> main
      else -> "$main $right"
    }
  }
}
