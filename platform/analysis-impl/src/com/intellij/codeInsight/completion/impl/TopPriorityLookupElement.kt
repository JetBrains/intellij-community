// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.impl

import com.intellij.codeInsight.completion.impl.TopPriorityLookupElement.FORCE_FIRST_SELECT
import com.intellij.codeInsight.completion.impl.TopPriorityLookupElement.NEVER_AUTOSELECT_TOP_PRIORITY_ITEM
import com.intellij.codeInsight.completion.impl.TopPriorityLookupElement.TOP_PRIORITY_ITEM
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
object TopPriorityLookupElement {

  /**
   * See [com.intellij.codeInsight.lookup.LookupArranger.isTopPriorityItem].
   *
   * This key forces an item to be placed at the very top of a lookup. It ignores sorting policies.
   */
  @JvmField
  internal val TOP_PRIORITY_ITEM: Key<Boolean> = Key.create("completion.lookup.top.priority.item")

  /**
   * If [com.intellij.codeInsight.lookup.LookupElement] does not contain [TOP_PRIORITY_ITEM], it doesn't affect any behavior.
   *
   * Otherwise, If [com.intellij.codeInsight.lookup.LookupElement] contains this key and at the first place, it will be semi-selected
   */
  @JvmField
  internal val FORCE_FIRST_SELECT: Key<Boolean> = Key.create("completion.lookup.force.autoselect.top.priority.item")

  /**
   * If [com.intellij.codeInsight.lookup.LookupElement] does not contain [TOP_PRIORITY_ITEM], it doesn't affect any behavior.
   *
   * Otherwise, If [com.intellij.codeInsight.lookup.LookupElement] contains this key, it is never selected by default in a lookup
   * (the first selection when a lookup appears). With a few exceptions:
   * * A lookup consists only of such elements
   * * A element is an exact match with prefix.
   */
  @JvmField
  internal val NEVER_AUTOSELECT_TOP_PRIORITY_ITEM: Key<Boolean> = Key.create("completion.lookup.never.autoselect.top.priority.item")

  /**
   * Makes [item] a top-priority element which is always placed at the top of a lookup.
   *
   * If [neverAutoselect] is `true`, then it's never autoselected on the first appearance of a lookup (some element below is selected).
   *
   * @see TOP_PRIORITY_ITEM
   * @see NEVER_AUTOSELECT_TOP_PRIORITY_ITEM
   */
  fun <T : LookupElement> prioritizeToTop(item: T, neverAutoselect: Boolean): T {
    item.putUserData(TOP_PRIORITY_ITEM, true)
    item.putUserData(NEVER_AUTOSELECT_TOP_PRIORITY_ITEM, neverAutoselect)
    return item
  }

  /**
   * Makes [item] a top-priority element which is always placed at the top of a lookup and tried to be selected.
   **
   * @see TOP_PRIORITY_ITEM
   * @see FORCE_FIRST_SELECT
   */
  @ApiStatus.Internal
  fun <T : LookupElement> prioritizeToTopAndSelect(item: T): T {
    item.putUserData(TOP_PRIORITY_ITEM, true)
    item.putUserData(FORCE_FIRST_SELECT, true)
    return item
  }
}
