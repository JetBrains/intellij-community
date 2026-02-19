// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.TailType
import com.intellij.codeInsight.completion.serialization.InsertHandlerSerializer
import com.intellij.codeInsight.completion.serialization.TailTypeSerializer
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import kotlinx.serialization.Serializable


@Serializable
internal data class FrontendFriendlyOverridableSpaceInsertHandler(
  val delegateInsertHandler: FrontendFriendlyInsertHandler?,
  val tailType: FrontendFriendlyTailType,
) : FrontendFriendlyInsertHandler {

  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    require(tailType is TailType) { "Tail type must extend TailType: $tailType" }
    val wrapper = LookupElementBuilder.create("").withInsertHandler(delegateInsertHandler)
    val lookupElement = OverridableSpace.create(wrapper, tailType)
    lookupElement.handleInsert(context)
  }

  companion object {
    @JvmStatic
    fun createIfFrontendFriendly(os: OverridableSpace): FrontendFriendlyOverridableSpaceInsertHandler? {
      // Try to convert tail type to frontend-friendly version
      val tail = os.myTail
      val ffTailType = tail as? FrontendFriendlyTailType ?: TailTypeSerializer.toDescriptor(tail) ?: return null

      // Try to get delegate's effective handler
      val delegateHandler = os.delegateEffectiveInsertHandler
      val ffDelegate = if (delegateHandler != null) {
        delegateHandler as? FrontendFriendlyInsertHandler ?: InsertHandlerSerializer.toDescriptor(delegateHandler)
        ?: return null // delegate handler must be frontend-friendly
      }
      else {
        null // missing delegate handler is OK
      }

      return FrontendFriendlyOverridableSpaceInsertHandler(ffDelegate, ffTailType)
    }
  }
}