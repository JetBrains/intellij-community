// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementInsertStopper
import com.intellij.codeInsight.lookup.LookupElementPresentation
import kotlinx.serialization.Serializable

@Serializable
data class RpcCompletionItem(
  val lookupString: String,
  val allLookupStrings: Set<String>,
  val presentation: RpcCompletionItemPresentation,
  val id: RpcCompletionItemId,
  val insertHandler: RpcInsertHandler,
  val requiresCommittedDocuments: Boolean,
  val autoCompletionPolicy: AutoCompletionPolicy,
  val isCaseSensitive: Boolean,
  val shouldStopLookupInsertion: Boolean,
  val isDirectInsertion: Boolean,
)

fun LookupElement.toRpc(): RpcCompletionItem {
  val presentation = render()
  return RpcCompletionItem(
    lookupString = this.lookupString,
    allLookupStrings = this.allLookupStrings,
    presentation = presentation.toRpc(),
    id = RpcCompletionItemId(),
    insertHandler = this.getRpcInsertHandler(),
    requiresCommittedDocuments = this.requiresCommittedDocuments(),
    autoCompletionPolicy = this.autoCompletionPolicy,
    isCaseSensitive = this.isCaseSensitive,
    shouldStopLookupInsertion = this is LookupElementInsertStopper && this.shouldStopLookupInsertion(),
    isDirectInsertion = this.getUserData(CodeCompletionHandlerBase.DIRECT_INSERTION) != null
  )
}

private fun LookupElement.render(): LookupElementPresentation {
  val presentation = LookupElementPresentation()
  this.renderElement(presentation)
  return presentation
}
