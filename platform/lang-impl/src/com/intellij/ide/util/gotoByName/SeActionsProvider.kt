// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.ide.actions.searcheverywhere.footer.ActionHistoryManager
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.Utils.runSuspendingUpdateSessionForActionSearch
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.searchEverywhere.SeItemPresentation
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.api.SeItem
import com.intellij.platform.searchEverywhere.api.SeItemsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.ApiStatus
import java.awt.Component


@ApiStatus.Internal
class SeActionsProvider(project: Project? = null, contextComponent: Component? = null, editor: Editor? = null): SeItemsProvider {
  constructor(): this(null, null, null)

  override val id: String get() = "com.intellij.ActionsItemsProvider"
  private val model: GotoActionModel = GotoActionModel(project, contextComponent, editor)
  private val asyncProvider: ActionAsyncProvider = ActionAsyncProvider(model)

  override fun getItems(params: SeParams): Flow<SeItem> {
    return channelFlow {
      val filter = SeActionsFilterData.fromTabData(params.filterData)
      processItems(params.text, filter.includeDisabled) { value, weight ->
        val item = ActionSearchItem(weight, value)
        channel.send(item)
        coroutineContext.isActive
      }
    }
  }

  private suspend fun processItems(text: String, includeDisabled: Boolean, processor: suspend (MatchedValue, Int) -> Boolean) {
    model.buildGroupMappings()
    runSuspendingUpdateSessionForActionSearch(model.getUpdateSession()) { presentationProvider ->
      if (text.isEmpty() && isRecentsShown()) {
        processRecents(text, includeDisabled, presentationProvider, processor)
      }
      else {
        processAllItems(text, includeDisabled, presentationProvider, processor)
      }
    }
  }

  private fun CoroutineScope.processAllItems(text: String, includeDisabled: Boolean, presentationProvider: suspend (AnAction) -> Presentation, processor: suspend (MatchedValue, Int) -> Boolean) {
    asyncProvider.filterElements(this, presentationProvider, text) { matchedValue ->
      if (includeDisabled) {
        val enabled = (matchedValue.value as? GotoActionModel.ActionWrapper)?.isAvailable != false
        if (!enabled) return@filterElements true
      }

      processor(matchedValue, matchedValue.matchingDegree)
    }

  }

  private fun CoroutineScope.processRecents(text: String, includeDisabled: Boolean, presentationProvider: suspend (AnAction) -> Presentation, processor: suspend (MatchedValue, Int) -> Boolean) {
    val actionIDs: Set<String> = ActionHistoryManager.getInstance().state.ids
    asyncProvider.processActions(this, presentationProvider, text, actionIDs) { matchedValue ->
      if (!includeDisabled) {
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
class ActionSearchItem(private val weight: Int, private val matchedValue: MatchedValue): SeItem {
  override fun weight(): Int = weight
  override fun presentation(): SeItemPresentation = ActionPresentationProvider.invoke(matchedValue)
}
