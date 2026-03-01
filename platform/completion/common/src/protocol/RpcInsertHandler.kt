// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.FrontendFriendlyInsertHandler
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.LookupElementWithEffectiveInsertHandler
import com.intellij.codeInsight.completion.serialization.InsertHandlerSerializer
import com.intellij.codeInsight.lookup.LookupElement
import kotlinx.serialization.Serializable

/**
 * Represents [InsertHandler] in the completion RPC protocol.
 *
 * Note that [LookupElement] can either use an [InsertHandler] or just implement [LookupElement.handleInsert].
 * Both cases are handled here. [LookupElement.handleInsert] is considered a special case of [InsertHandler].
 *
 * @see LookupElementWithEffectiveInsertHandler
 * @see FrontendFriendlyInsertHandler
 */
@Serializable
sealed interface RpcInsertHandler {
  /**
   * Is used when any conditions are met:
   *  1. A non-frontend-friendly insert handler is specified for the [LookupElement]
   *  2. [LookupElement] has custom logic in its [LookupElement.handleInsert]
   *
   * In this case, the insert handler must run on the backend side.
   */
  @Serializable
  object Backend : RpcInsertHandler

  /**
   * Is used when a frontend-friendly insert handler is specified for the [LookupElement].
   * In this case, we can run it on Frontend.
   */
  @Serializable
  data class Frontend(
    @Serializable(with = InsertHandlerSerializer::class)
    val insertHandler: FrontendFriendlyInsertHandler,
  ) : RpcInsertHandler {
    override fun toString(): String = buildToString("Frontend") {
      field("insertHandler", insertHandler)
    }
  }
}

fun LookupElement.getRpcInsertHandler(): RpcInsertHandler {
  val lookupElementWithEffectiveInsertHandler = this as? LookupElementWithEffectiveInsertHandler ?: return RpcInsertHandler.Backend
  val effectiveInsertHandler = lookupElementWithEffectiveInsertHandler.effectiveInsertHandler ?: return RpcInsertHandler.Backend
  val frontendFriendlyInsertHandler = InsertHandlerSerializer.toDescriptor(effectiveInsertHandler) ?: return RpcInsertHandler.Backend
  return RpcInsertHandler.Frontend(frontendFriendlyInsertHandler)
}
