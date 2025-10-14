// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import kotlinx.serialization.Serializable

/**
 * Represents [InsertHandler] in the completion RPC protocol.
 *
 * Note that [LookupElement] can either use an [InsertHandler] or just implement [LookupElement.handleInsert].
 * Both cases are handled here. [LookupElement.handleInsert] is considered a special case of [InsertHandler].
 *
 * @see FrontendFriendlyLookupElement
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
    val insertHandler: FrontendFriendlyInsertHandler,
  ) : RpcInsertHandler
}

fun LookupElement.getRpcInsertHandler(): RpcInsertHandler {
  val handler = when {
    this is FrontendFriendlyLookupElement -> {
      when (val frontendFriendly = this.frontendFriendlyInsertHandler) {
        null -> RpcInsertHandler.Backend
        else -> RpcInsertHandler.Frontend(frontendFriendly)
      }
    }
    this is LookupElementBuilder -> {
      this.insertHandler?.toRpc() ?: RpcInsertHandler.Frontend(NoOpFrontendFriendlyInsertHandler)
    }
    this is TransparentForInsertHandling && this is LookupElementDecorator<*> -> {
      this.delegate.getRpcInsertHandler()
    }
    LookupElementDecorator.isDecoratedWithInsertHandler(this) -> {
      (this as LookupElementDecorator<*>).decoratorInsertHandler!!.toRpc()
    }
    else -> {
      RpcInsertHandler.Backend
    }
  }

  return handler
}

private fun InsertHandler<*>.toRpc(): RpcInsertHandler {
  return when (this) {
    is FrontendFriendlyInsertHandler -> RpcInsertHandler.Frontend(this)
    else -> RpcInsertHandler.Backend
  }
}
