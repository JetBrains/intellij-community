// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

class MockActionSearchEverywhereItem(val text: String): SearchEverywhereItem {
  override fun weight(): Int = 0
  override fun presentation(): SearchEverywhereItemPresentation = SearchEverywhereTextItemPresentation(text)
}


@ApiStatus.Internal
class SearchEverywhereActionsProvider(project: Project? = null, contextComponent: Component? = null, editor: Editor? = null): SearchEverywhereItemsProvider {
  constructor(): this(null, null, null)

  override val id: String get() = "com.intellij.ActionsItemsProvider"
  //private val model: GotoActionModel = GotoActionModel(project, contextComponent, editor)
  //private val asyncProvider: ActionAsyncProvider = ActionAsyncProvider(model)

  override fun getItems(params: SearchEverywhereParams): Flow<SearchEverywhereItem> {
    return (1..100).map {
      MockActionSearchEverywhereItem("ActionItem $it")
    }.asFlow()
    //channelFlow {
    //  processItems(params) { value, weight ->
    //    val item = ActionSearchItem(weight, value)
    //    channel.send(item)
    //    coroutineContext.isActive
    //  }
    //}
  }

  //private suspend fun processItems(params: SearchEverywhereParams, processor: suspend (MatchedValue, Int) -> Boolean) {
  //  if (params !is ActionSearchParams) return
  //
  //  model.buildGroupMappings()
  //  runSuspendingUpdateSessionForActionSearch(model.getUpdateSession()) { presentationProvider ->
  //    if (params.text.isEmpty() && isRecentsShown()) {
  //      processRecents(params, presentationProvider, processor)
  //    }
  //    else {
  //      processAllItems(params, presentationProvider, processor)
  //    }
  //  }
  //}
  //
  //private fun CoroutineScope.processAllItems(params: ActionSearchParams, presentationProvider: suspend (AnAction) -> Presentation, processor: suspend (MatchedValue, Int) -> Boolean) {
  //  asyncProvider.filterElements(this, presentationProvider, params.text) { matchedValue ->
  //    if (!params.includeDisabled) {
  //      val enabled = (matchedValue.value as? GotoActionModel.ActionWrapper)?.isAvailable != false
  //      if (!enabled) return@filterElements true
  //    }
  //
  //    processor(matchedValue, matchedValue.matchingDegree)
  //  }
  //
  //}
  //
  //private fun CoroutineScope.processRecents(params: ActionSearchParams, presentationProvider: suspend (AnAction) -> Presentation, processor: suspend (MatchedValue, Int) -> Boolean) {
  //  val actionIDs: Set<String> = ActionHistoryManager.getInstance().state.ids
  //  asyncProvider.processActions(this, presentationProvider, params.text, actionIDs) { matchedValue ->
  //    if (!params.includeDisabled) {
  //      val enabled = (matchedValue.value as? GotoActionModel.ActionWrapper)?.isAvailable != false
  //      if (!enabled) return@processActions true
  //    }
  //
  //    processor(matchedValue, matchedValue.matchingDegree)
  //  }
  //}
  //
  //private fun isRecentsShown(): Boolean {
  //  return Registry.`is`("search.everywhere.recents")
  //}
}

@ApiStatus.Internal
class ActionSearchItem(private val weight: Int, private val matchedValue: MatchedValue): SearchEverywhereItem {
  override fun weight(): Int = weight
  override fun presentation(): SearchEverywhereItemPresentation = ActionPresentationProvider.invoke(matchedValue)
}
