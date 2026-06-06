// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp

import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * Read-only view of process-launchable ACP agents currently known to the IDE.
 *
 * The catalog merges two sources:
 * - Local agents declared in `acp.json` (custom `AcpCustomAgentId`)
 * - External agents from the active registry, either CDN/local-file or JCP-provisioned
 *   (`AcpRegistryAgentId`)
 *
 * Local declarations take precedence when the same id appears in both sources.
 * Remote (URL-based) ACP agents are NOT included — they cannot be launched as a
 * process and so do not fit [AcpProcessLauncher]'s contract.
 *
 * Non-chat callers (code review, coding agents) should consult the catalog
 * instead of reaching into individual ACP services, so they pick up the same
 * agents that chat sees.
 */
interface AcpAgentsCatalog {
  /**
   * Snapshot of currently known entries. Updates as `acp.json` is edited or the
   * external registry is refreshed.
   */
  val agentsFlow: StateFlow<List<AcpCatalogEntry>>

  /** Returns the entry with the given id, or `null` if none is known. */
  fun get(id: AcpAgentId): AcpCatalogEntry?

  companion object {
    fun getInstance(): AcpAgentsCatalog = service<AcpAgentsCatalog>()
  }
}

@ApiStatus.Internal
interface AcpAgentsCatalogProvider {
  companion object {
    val EP_NAME: ExtensionPointName<AcpAgentsCatalogProvider> =
      ExtensionPointName.create("com.intellij.platform.acp.agentsCatalogProvider")
  }

  val agentsFlow: StateFlow<List<AcpCatalogEntry>>
}

/**
 * A single agent in the [AcpAgentsCatalog]. Resolving the start config may
 * trigger lazy binary or managed-runtime downloads for external agents.
 */
interface AcpCatalogEntry {
  val id: AcpAgentId
  val displayName: String
  val origin: AcpAgentOrigin

  suspend fun resolveStartConfig(project: Project): AcpAgentStartConfig
}

enum class AcpAgentOrigin {
  /** Declared in a local `acp.json` file. */
  LOCAL,

  /** Fetched from the external (CDN or local-file) registry. */
  EXTERNAL_REGISTRY,
}
