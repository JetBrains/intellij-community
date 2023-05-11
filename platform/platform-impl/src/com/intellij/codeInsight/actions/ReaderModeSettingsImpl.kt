// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.codeInsight.actions.ReaderModeProvider.ReaderMode
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.psi.codeStyle.CodeStyleScheme
import kotlinx.coroutines.CoroutineScope

@State(name = "ReaderModeSettings", storages = [
  Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE),
  Storage(StoragePathMacros.WORKSPACE_FILE, deprecated = true)
])
class ReaderModeSettingsImpl(override val coroutineScope: CoroutineScope) : PersistentStateComponentWithModificationTracker<ReaderModeSettingsImpl.State>, ReaderModeSettings {
  private var myState = State()

  class State : BaseState() {
    class SchemeState : BaseState() {
      var name by string(CodeStyleScheme.DEFAULT_SCHEME_NAME)
      var isProjectLevel by property(false)
    }

    var visualFormattingChosenScheme by property(SchemeState())
    @get:ReportValue var enableVisualFormatting by property(true)
    @get:ReportValue var useActiveSchemeForVisualFormatting by property(true)
    @get:ReportValue var showLigatures by property(EditorColorsManager.getInstance().globalScheme.fontPreferences.useLigatures())
    @get:ReportValue var increaseLineSpacing by property(false)
    @get:ReportValue var showRenderedDocs by property(true)
    @get:ReportValue var showInlayHints by property(true)
    @get:ReportValue var showWarnings by property(false)
    @get:ReportValue var enabled by property(Experiments.getInstance().isFeatureEnabled("editor.reader.mode"))

    var mode: ReaderMode = ReaderMode.LIBRARIES_AND_READ_ONLY
  }

  override fun dispose() {
  }

  override var visualFormattingChosenScheme: ReaderModeSettings.Scheme
    get() = state.visualFormattingChosenScheme.let { ReaderModeSettings.Scheme(it.name, it.isProjectLevel) }
    set(value) {
      state.visualFormattingChosenScheme = State.SchemeState().apply {
        name = value.name
        isProjectLevel = value.isProjectLevel
      }
    }

  override var useActiveSchemeForVisualFormatting: Boolean
    get() = state.useActiveSchemeForVisualFormatting
    set(value) {
      state.useActiveSchemeForVisualFormatting = value
    }

  override var enableVisualFormatting: Boolean
    get() = state.enableVisualFormatting
    set(value) {
      state.enableVisualFormatting = value
    }

  override var showLigatures: Boolean
    get() = state.showLigatures
    set(value) {
      state.showLigatures = value
    }

  override var increaseLineSpacing: Boolean
    get() = state.increaseLineSpacing
    set(value) {
      state.increaseLineSpacing = value
    }

  override var showInlaysHints: Boolean
    get() = state.showInlayHints
    set(value) {
      state.showInlayHints = value
    }

  override var showRenderedDocs: Boolean
    get() = state.showRenderedDocs
    set(value) {
      state.showRenderedDocs = value
    }

  override var showWarnings: Boolean
    get() = state.showWarnings
    set(value) {
      state.showWarnings = value
    }

  override var enabled: Boolean
    get() = state.enabled
    set(value) {
      state.enabled = value
    }

  override var mode: ReaderMode
    get() = state.mode
    set(value) {
      state.mode = value
    }

  override fun getState(): State = myState

  override fun loadState(state: State) {
    myState = state
  }

  override fun getStateModificationCount() = state.modificationCount
}