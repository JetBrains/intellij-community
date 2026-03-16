// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionItemLookupElement
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.command.RemDevCommandCompletionHelpers
import com.intellij.codeInsight.completion.impl.TopPriorityLookupElement
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementInsertStopper
import com.intellij.codeInsight.lookup.LookupElementPresentation
import kotlinx.serialization.Serializable

@Serializable
data class RpcCompletionItem(
  val id: RpcCompletionItemId,
  val prefixMatcher: RpcPrefixMatcher,
  val insertHandler: RpcInsertHandler = RpcInsertHandler.Backend,
  val lookupString: String,
  val allLookupStrings: Set<String>? = null, // null means setOf(lookupString)
  val presentation: RpcCompletionItemPresentation,
  val hasExpensiveRenderer: Boolean = false,
  val requiresCommittedDocuments: Boolean = true,
  val autoCompletionPolicy: AutoCompletionPolicy = AutoCompletionPolicy.SETTINGS_DEPENDENT,
  val isCaseSensitive: Boolean = true,
  val shouldStopLookupInsertion: Boolean = false,
  val isDirectInsertion: Boolean = false,
  val isWorthShowingInAutoPopup: Boolean = false,
  val commandState: RemDevCommandCompletionHelpers.CommandState? = null,
  val hasModCommand: Boolean = false,
  val isTopPriorityItem: Boolean = false,
  val isNeverAutoselectTopPriorityItem: Boolean = false,
) {
  override fun toString(): String = buildToString("RpcCompletionItem") {
    field("id", id)
    field("prefixMatcher", prefixMatcher)
    fieldWithDefault("insertHandler", insertHandler, RpcInsertHandler.Backend)
    field("lookupString", lookupString)
    fieldWithNullDefault("allLookupStrings", allLookupStrings)
    field("presentation", presentation)
    fieldWithDefault("hasExpensiveRenderer", hasExpensiveRenderer, false)
    fieldWithDefault("requiresCommittedDocuments", requiresCommittedDocuments, true)
    fieldWithDefault("autoCompletionPolicy", autoCompletionPolicy, AutoCompletionPolicy.SETTINGS_DEPENDENT)
    fieldWithDefault("isCaseSensitive", isCaseSensitive, true)
    fieldWithDefault("shouldStopLookupInsertion", shouldStopLookupInsertion, false)
    fieldWithDefault("isDirectInsertion", isDirectInsertion, false)
    fieldWithDefault("isWorthShowingInAutoPopup", isWorthShowingInAutoPopup, false)
    fieldWithDefault("hasModCommand", hasModCommand, false)
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
    isWorthShowingInAutoPopup = element.isWorthShowingInAutoPopup(),
    commandState = RemDevCommandCompletionHelpers.getCommandState(element),
    hasModCommand = element is CompletionItemLookupElement,
    isTopPriorityItem = TopPriorityLookupElement.isTopPriorityItem(element),
    isNeverAutoselectTopPriorityItem = TopPriorityLookupElement.isNeverAutoselectTopPriorityItem(element),
  )
}

private fun LookupElement.render(): LookupElementPresentation {
  val presentation = LookupElementPresentation()
  this.renderElement(presentation)
  return presentation
}
