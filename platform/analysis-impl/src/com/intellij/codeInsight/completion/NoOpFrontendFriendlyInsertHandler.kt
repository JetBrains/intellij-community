// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElement
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * A [FrontendFriendlyInsertHandler] that does nothing.
 *
 * Return it from [InsertHandlerToFrontendFriendlyConverter.toDescriptor] when the insert handler has nothing to do on Frontend.
 * A `null` result has a different meaning: the lookup element keeps its insert handler on Backend.
 */
@ApiStatus.Experimental
@Serializable
object NoOpFrontendFriendlyInsertHandler : FrontendFriendlyInsertHandler {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {}
}