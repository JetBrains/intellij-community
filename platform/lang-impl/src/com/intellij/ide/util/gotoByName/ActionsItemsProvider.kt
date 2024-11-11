// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.ide.actions.searcheverywhere.footer.ActionHistoryManager
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.Utils.runUpdateSessionForActionSearch
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhere.SearchEverywhereItemsProvider
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

@ApiStatus.Internal
class ActionsItemsProvider(project: Project?, contextComponent: Component?, editor: Editor?): SearchEverywhereItemsProvider<MatchedValue, ActionSearchParams> {

  private val model: GotoActionModel = GotoActionModel(project, contextComponent, editor)
  private val asyncProvider: ActionAsyncProvider = ActionAsyncProvider(model)

  override suspend fun processItems(scope: CoroutineScope, searchParams: ActionSearchParams, processor: (MatchedValue, Int) -> Boolean) {
    model.buildGroupMappings()
    scope.runUpdateSessionForActionSearch(model.getUpdateSession()) { presentationProvider ->
      if (searchParams.pattern.isEmpty() && isRecentsShown()) {
        processRecents(searchParams, presentationProvider, processor)
      }
      else {
        processAllItems(searchParams, presentationProvider, processor)
      }
    }
  }

  private fun CoroutineScope.processAllItems(params: ActionSearchParams, presentationProvider: suspend (AnAction) -> Presentation, processor: (MatchedValue, Int) -> Boolean) {
    asyncProvider.filterElements(this, presentationProvider, params.pattern) { matchedValue ->
      if (!params.includeDisabled) {
        val enabled = (matchedValue.value as? GotoActionModel.ActionWrapper)?.isAvailable != false
        if (!enabled) return@filterElements true
      }

      processor(matchedValue, matchedValue.matchingDegree)
    }

  }

  private fun CoroutineScope.processRecents(params: ActionSearchParams, presentationProvider: suspend (AnAction) -> Presentation, processor: (MatchedValue, Int) -> Boolean) {
    val actionIDs: Set<String> = ActionHistoryManager.getInstance().state.ids
    asyncProvider.processActions(this, presentationProvider, params.pattern, actionIDs) { matchedValue ->
      if (!params.includeDisabled) {
        val enabled = (matchedValue.value as? GotoActionModel.ActionWrapper)?.isAvailable != false
        if (!enabled) return@processActions true
      }

      processor(matchedValue, matchedValue.matchingDegree)
    }
  }

  private fun isRecentsShown(): Boolean {
    return Registry.`is`("search.everywhere.recents")
  }
}

@ApiStatus.Internal
data class ActionSearchParams(val pattern: String, val includeDisabled: Boolean)
