// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.impl

import com.intellij.codeInsight.completion.impl.LookupCompletionPriorityItemKeys.TOP_PRIORITY_ITEM
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object LookupCompletionPriorityItemKeys {

  /**
   * See [com.intellij.codeInsight.lookup.LookupArranger.isTopPriorityItem].
   *
   * This key forces an item to be placed at the very top of a lookup. It ignores sorting policies.
   */
  @JvmField
  val TOP_PRIORITY_ITEM: Key<Boolean> = Key.create("completion.lookup.top.priority.item")

  /**
   * If [com.intellij.codeInsight.lookup.LookupElement] does not contain [TOP_PRIORITY_ITEM], it doesn't affect any behavior.
   *
   * Otherwise, If [com.intellij.codeInsight.lookup.LookupElement] contains this key, it is never selected by default in a lookup
   * (the first selection when a lookup appears). With a few exceptions:
   * * A lookup consists only of such elements
   * * A element is an exact match with prefix.
   */
  @JvmField
  val NEVER_AUTOSELECT_TOP_PRIORITY_ITEM: Key<Boolean> = Key.create("completion.lookup.never.autoselect.top.priority.item")
}
