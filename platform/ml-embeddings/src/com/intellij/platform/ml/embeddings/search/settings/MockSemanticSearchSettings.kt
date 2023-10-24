// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.settings

import com.intellij.openapi.components.Service

@Service(Service.Level.APP)
class MockSemanticSearchSettings : SemanticSearchSettings {
  override var enabledInActionsTab = false
  override var enabledInFilesTab = false
  override var enabledInSymbolsTab = false
  override var enabledInClassesTab = false

  override fun isEnabled() = true

  override fun getUseRemoteActionsServer() = false
  override fun getActionsAPIToken() = ""
}