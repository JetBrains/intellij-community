// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object FusCompletionKeys {
  @JvmField
  val LOOKUP_ELEMENT_CONTRIBUTOR: Key<CompletionContributor> = Key.create("lookup element contributor")

  /**
   * Timestamp when a lookup item was added to the [CompletionResultSet]
   */
  @JvmField
  val LOOKUP_ELEMENT_RESULT_ADD_TIMESTAMP_MILLIS: Key<Long> = Key.create<Long>("lookup element add time")

  /**
   * The order in which the element was added to the [CompletionResultSet]
   */
  @JvmField
  val LOOKUP_ELEMENT_RESULT_SET_ORDER: Key<Int> = Key.create<Int>("lookup element result set order")

  /**
   * The timestamp when the item was shown in the lookup window during the completion session for the first time.
   * It is recorded only if it is used later by completion logs [com.intellij.stats.completion.tracker.CompletionLogger]
   * or FUS logs of the completion [com.intellij.codeInsight.lookup.impl.LookupUsageTracker]
   */
  @JvmField
  val LOOKUP_ELEMENT_SHOW_TIMESTAMP_MILLIS: Key<Long> = Key.create("lookup element shown timestamp")

}
