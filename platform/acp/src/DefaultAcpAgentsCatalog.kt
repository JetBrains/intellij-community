// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fallback [AcpAgentsCatalog] shipped with the platform so the service interface is always
 * resolvable. Reports an empty catalog — the real implementation in the AI Assistant plugin
 * overrides this one via `overrides="true"` and exposes the actual local/registry agents.
 */
internal class DefaultAcpAgentsCatalog : AcpAgentsCatalog {
  override val agentsFlow: StateFlow<List<AcpCatalogEntry>> = MutableStateFlow(emptyList())

  override fun get(id: AcpAgentId): AcpCatalogEntry? = null
}
