// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.embeddings.search.services.ActionEmbeddingsStorage
import com.intellij.platform.ml.embeddings.search.services.ClassEmbeddingsStorage
import com.intellij.platform.ml.embeddings.search.services.FileEmbeddingsStorage
import com.intellij.platform.ml.embeddings.search.services.SymbolEmbeddingStorage
import com.intellij.util.xmlb.annotations.OptionTag


@Service(Service.Level.APP)
@State(
  name = "SemanticSearchSettings",
  storages = [Storage(value = "semantic-search-settings.xml", roamingType = RoamingType.DISABLED, exportable = true)]
)
class SemanticSearchSettingsImpl : SemanticSearchSettings, PersistentStateComponent<SemanticSearchSettingsState> {
  private var state = SemanticSearchSettingsState()

  override var enabledInActionsTab: Boolean
    get() = state.enabledInActionsTab
    set(newValue) {
      if (!newValue) {
        state.manuallyDisabledInActionsTab = true
      } else if (state.manuallyDisabledInActionsTab) {
        state.manuallyDisabledInActionsTab = false
      }
      state.enabledInActionsTab = newValue
      ProjectManager.getInstance().openProjects.first().let {
        ActionEmbeddingsStorage.getInstance().run { if (newValue) prepareForSearch(it) else tryStopGeneratingEmbeddings() }
      }
    }

  override var enabledInFilesTab: Boolean
    get() = state.enabledInFilesTab
    set(newValue) {
      if (!newValue) {
        state.manuallyDisabledInFilesTab = true
      } else if (state.manuallyDisabledInFilesTab) {
        state.manuallyDisabledInFilesTab = false
      }
      state.enabledInFilesTab = newValue
      ProjectManager.getInstance().openProjects.forEach {
        FileEmbeddingsStorage.getInstance(it).run { if (newValue) prepareForSearch() else tryStopGeneratingEmbeddings()}
      }
    }

  override var enabledInSymbolsTab: Boolean
    get() = state.enabledInSymbolsTab
    set(newValue) {
      if (!newValue) {
        state.manuallyDisabledInSymbolsTab = true
      } else if (state.manuallyDisabledInSymbolsTab) {
        state.manuallyDisabledInSymbolsTab = false
      }
      state.enabledInSymbolsTab = newValue
      ProjectManager.getInstance().openProjects.forEach {
        SymbolEmbeddingStorage.getInstance(it).run { if (newValue) prepareForSearch() else tryStopGeneratingEmbeddings() }
      }
    }

  override var enabledInClassesTab: Boolean
    get() = state.enabledInClassesTab
    set(newValue) {
      if (!newValue) {
        state.manuallyDisabledInClassesTab = true
      } else if (state.manuallyDisabledInClassesTab) {
        state.manuallyDisabledInClassesTab = false
      }
      state.enabledInClassesTab = newValue
      ProjectManager.getInstance().openProjects.forEach {
        ClassEmbeddingsStorage.getInstance(it).run { if (newValue) prepareForSearch() else tryStopGeneratingEmbeddings() }
      }
    }

  override val manuallyDisabledInActionsTab: Boolean
    get() = state.manuallyDisabledInActionsTab

  override val manuallyDisabledInFilesTab: Boolean
    get() = state.manuallyDisabledInFilesTab

  override val manuallyDisabledInSymbolsTab: Boolean
    get() = state.manuallyDisabledInSymbolsTab

  override val manuallyDisabledInClassesTab: Boolean
    get() = state.manuallyDisabledInClassesTab

  override fun getState(): SemanticSearchSettingsState = state

  override fun loadState(newState: SemanticSearchSettingsState) {
    state = newState
  }

  override fun isEnabled() = enabledInActionsTab || enabledInFilesTab || enabledInSymbolsTab || enabledInClassesTab

  override fun getUseRemoteActionsServer() = Registry.`is`("search.everywhere.ml.semantic.actions.server.use")

  override fun getActionsAPIToken(): String = Registry.stringValue("search.everywhere.ml.semantic.actions.server.token")

  companion object {
    fun getInstance(): SemanticSearchSettings = service()
  }
}

class SemanticSearchSettingsState : BaseState() {
  @get:OptionTag("enabled_in_actions_tab")
  var enabledInActionsTab by property(false)

  @get:OptionTag("enabled_in_files_tab")
  var enabledInFilesTab by property(false)

  @get:OptionTag("enabled_in_symbols_tab")
  var enabledInSymbolsTab by property(false)

  @get:OptionTag("enabled_in_classes_tab")
  var enabledInClassesTab by property(false)

  @get:OptionTag("manually_disabled_in_actions_tab")
  var manuallyDisabledInActionsTab by property(false)

  @get:OptionTag("manually_disabled_in_files_tab")
  var manuallyDisabledInFilesTab by property(false)

  @get:OptionTag("manually_disabled_in_symbols_tab")
  var manuallyDisabledInSymbolsTab by property(false)

  @get:OptionTag("manually_disabled_in_classes_tab")
  var manuallyDisabledInClassesTab by property(false)
}