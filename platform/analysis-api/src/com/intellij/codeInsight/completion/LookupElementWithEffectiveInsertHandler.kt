// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import org.jetbrains.annotations.ApiStatus

/**
 * Please prefer switching to [com.intellij.codeInsight.lookup.LookupElementBuilder] instead.
 *
 * Marker interface to be used for lookup elements that have an insert handler that is guaranteed to be used for insert-handling.
 * It is necessary for lookup elements to be able to be transferred to the frontend without losing their insert handler.
 */
@ApiStatus.Internal
interface LookupElementWithEffectiveInsertHandler {
  /**
   * @return effective insert handler for the lookup element, or `null` if it is not possible.
   */
  val effectiveInsertHandler: InsertHandler<*>?
}