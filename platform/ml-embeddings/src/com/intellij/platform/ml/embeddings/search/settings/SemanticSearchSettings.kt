// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.settings

import com.intellij.openapi.components.service

interface SemanticSearchSettings {
  var enabledInActionsTab: Boolean
  var enabledInFilesTab: Boolean
  var enabledInSymbolsTab: Boolean
  var enabledInClassesTab: Boolean

  val manuallyDisabledInActionsTab: Boolean
    get() = false
  val manuallyDisabledInFilesTab: Boolean
    get() = false
  val manuallyDisabledInSymbolsTab: Boolean
    get() = false
  val manuallyDisabledInClassesTab: Boolean
    get() = false

  fun isEnabled(): Boolean
  fun isEnabledFileRelated(): Boolean = enabledInClassesTab || enabledInFilesTab || enabledInSymbolsTab

  fun getUseRemoteActionsServer(): Boolean
  fun getActionsAPIToken(): String

  companion object {
    fun getInstance(): SemanticSearchSettings = service<SemanticSearchSettings>()
  }
}