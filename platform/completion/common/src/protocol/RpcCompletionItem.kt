// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementInsertStopper
import com.intellij.codeInsight.lookup.LookupElementPresentation
import kotlinx.serialization.Serializable

@Serializable
data class RpcCompletionItem(
  val lookupString: String,
  val allLookupStrings: Set<String>? = null, // null means setOf(lookupString)
  val presentation: RpcCompletionItemPresentation,
  val id: RpcCompletionItemId,
  val hasExpensiveRenderer: Boolean = false,
  val insertHandler: RpcInsertHandler = RpcInsertHandler.Backend,
  val requiresCommittedDocuments: Boolean = true,
  val autoCompletionPolicy: AutoCompletionPolicy = AutoCompletionPolicy.SETTINGS_DEPENDENT,
  val isCaseSensitive: Boolean = true,
  val shouldStopLookupInsertion: Boolean = false,
  val isDirectInsertion: Boolean = false,
  val prefixMatcher: RpcPrefixMatcher,
) {
  override fun toString(): String = buildToString("RpcCompletionItem") {
    field("lookupString", lookupString)
    fieldWithNullDefault("allLookupStrings", allLookupStrings)
    field("presentation", presentation)
    field("id", id)
    fieldWithDefault("insertHandler", insertHandler, RpcInsertHandler.Backend)
    fieldWithDefault("requiresCommittedDocuments", requiresCommittedDocuments, true)
    fieldWithDefault("autoCompletionPolicy", autoCompletionPolicy, AutoCompletionPolicy.SETTINGS_DEPENDENT)
    fieldWithDefault("isCaseSensitive", isCaseSensitive, true)
    fieldWithDefault("shouldStopLookupInsertion", shouldStopLookupInsertion, false)
    fieldWithDefault("isDirectInsertion", isDirectInsertion, false)
    field("prefixMatcher", prefixMatcher)
  }
}

fun CompletionResult.toRpc(): RpcCompletionItem {
  val element = this.lookupElement
  val prefixMatcher = this.prefixMatcher
  val presentation = element.render()
  val id = RpcCompletionItemId()
  return RpcCompletionItem(
    lookupString = element.lookupString,
    allLookupStrings = element.allLookupStrings.takeUnless { it.singleOrNull() == element.lookupString },
    presentation = presentation.toRpc(),
    id = id,
    insertHandler = element.getRpcInsertHandler(),
    requiresCommittedDocuments = element.requiresCommittedDocuments(),
    hasExpensiveRenderer = element.expensiveRenderer != null,
    autoCompletionPolicy = element.autoCompletionPolicy,
    isCaseSensitive = element.isCaseSensitive,
    shouldStopLookupInsertion = element is LookupElementInsertStopper && element.shouldStopLookupInsertion(),
    isDirectInsertion = element.getUserData(CodeCompletionHandlerBase.DIRECT_INSERTION) != null,
    prefixMatcher = prefixMatcher.toRpc(id),
  )
}

private fun LookupElement.render(): LookupElementPresentation {
  val presentation = LookupElementPresentation()
  this.renderElement(presentation)
  return presentation
}
