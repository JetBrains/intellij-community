// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.diagnostic.debug
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

//TODO IJPL-207762 mark experimental

//TODO IJPL-207762 This class should be moved to `intellij.platform.completion.common` module once Java plugin is migrated to plugin model v2.
//                 At the moment, it's impossible as v1 modules don't see v2 modules, so Java could not have seen this class if it was in v2 module.

/**
 * A frontend-friendly insert handler that delegates to a list of other frontend-friendly insert handlers.
 * Can be useful if you want to split a complex insert handler into multiple independent pieces.
 *
 * @see FrontendFriendlyInsertHandler
 */
@ApiStatus.Internal
@Serializable
data class CompositeFrontendFriendlyInsertHandler(
  val debugName: String,
  private val children: List<FrontendFriendlyInsertHandler>,
) : FrontendFriendlyInsertHandler {

  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    logger.debug { "Handling insert \"$debugName\", item = $item, children = $children" }

    children.forEach {
      it.handleInsert(context, item)
    }
  }
}

private val logger = com.intellij.openapi.diagnostic.logger<CompositeFrontendFriendlyInsertHandler>()