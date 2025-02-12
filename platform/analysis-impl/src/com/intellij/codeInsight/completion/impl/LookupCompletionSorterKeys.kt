// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.impl

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object LookupCompletionSorterKeys {

  /**
   * See [com.intellij.codeInsight.lookup.LookupArranger.isTopPriorityItem].
   *
   * This key forces an item to be placed at the very top of a lookup. It ignores sorting policies.
   */
  @JvmField
  val TOP_PRIORITY_ITEM: Key<Boolean> = Key.create("completion.lookup.force.item.to.top")
}
