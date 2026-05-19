// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp

import kotlinx.serialization.Serializable

/**
 * Type-safe wrapper for ACP agent identifiers.
 *
 * ACP agents have two types:
 * - **Custom agents** (prefix: "acp.") - loaded from local `acp.json` files
 * - **Registry agents** (prefix: "acp.registry.") - loaded from the external registry
 *
 * Each type has its own ID format:
 * - **Raw ID**: The base identifier without prefix (e.g., "goose", "claude-code")
 * - **Full ID**: The prefixed identifier used by ChatAgent (e.g., "acp.goose", "acp.registry.goose")
 *
 * Usage:
 * ```
 * // Create from raw ID (when you know the type)
 * val customId = AcpCustomAgentId.fromRaw("goose")
 * val registryId = AcpRegistryAgentId.fromRaw("goose")
 *
 * // Create custom agent from display name
 * val fromName = AcpCustomAgentId.fromName("My Agent")
 *
 * // Parse from prefixed ID (throws on raw ID)
 * val parsed = AcpAgentId.parse("acp.registry.goose")
 *
 * // Access the format you need
 * id.rawId   // "goose" - for storage, file paths
 * id.fullId  // "acp.goose" or "acp.registry.goose" - for ChatAgent.id, UI
 * ```
 */
@Serializable
sealed interface AcpAgentId {
  /**
   * The raw agent ID without prefix (e.g., "goose").
   * Use for: storage, file paths, registry JSON, installation state.
   */
  val rawId: String

  /**
   * The full agent ID with prefix (e.g., "acp.goose" or "acp.registry.goose").
   * Use for: ChatAgent.id, UI display, popup handlers.
   */
  val fullId: String

  companion object {
    internal const val CUSTOM_PREFIX: String = "acp."
    internal const val REGISTRY_PREFIX: String = "${CUSTOM_PREFIX}registry."
    internal const val REMOTE_PREFIX: String = "${CUSTOM_PREFIX}remote."

    /**
     * Parses a prefixed agent ID string into the appropriate AcpAgentId type.
     *
     * This method is strict - it requires a valid prefix and throws on raw IDs.
     *
     * @param id The full agent ID with prefix (e.g., "acp.goose" or "acp.registry.goose")
     * @return The appropriate AcpAgentId subtype
     * @throws IllegalArgumentException if the ID doesn't have a valid ACP prefix
     */
    fun parse(id: String): AcpAgentId {
      return when {
        PREDEFINED_IDS.contains(id) -> AcpPredefinedAgentId(id)
        id.startsWith(REGISTRY_PREFIX) -> AcpRegistryAgentId(id.removePrefix(REGISTRY_PREFIX))
        id.startsWith(REMOTE_PREFIX) -> AcpRemoteAgentId(id.removePrefix(REMOTE_PREFIX))
        id.startsWith(CUSTOM_PREFIX) -> AcpCustomAgentId(id.removePrefix(CUSTOM_PREFIX))
        else -> throw IllegalArgumentException("Invalid ACP agent ID, missing prefix: $id")
      }
    }

    fun tryToParse(id: String): AcpAgentId? {
      if (!isAcpAgent(id)) return null
      return parse(id)
    }

    /**
     * Checks if a string represents any ACP agent ID (has "acp." or "acp.registry." prefix).
     *
     * @param id The ID to check
     * @return true if the ID starts with any ACP prefix
     */
    fun isAcpAgent(id: String): Boolean = id.startsWith(CUSTOM_PREFIX) || PREDEFINED_IDS.contains(id)


    /**
     * Sanitizes an agent name to create a valid agent ID.
     *
     * Converts the name to lowercase, replaces non-alphanumeric characters
     * with hyphens, and trims leading/trailing hyphens.
     *
     * Example: "My Custom Agent 🪿" -> "my-custom-agent"
     *
     * @param name The display name to sanitize
     * @return A sanitized ID suitable for use as an agent identifier
     */
    fun sanitize(name: String): String {
      return name
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    }
  }
}

data class AcpPredefinedAgentId(override val rawId: String) : AcpAgentId {
  override val fullId: String get() = rawId
}

/**
 * Represents a custom ACP agent ID (prefix: "acp.").
 * Custom agents are loaded from local `acp.json` files.
 */
data class AcpCustomAgentId(override val rawId: String) : AcpAgentId {
  override val fullId: String get() = "${AcpAgentId.CUSTOM_PREFIX}$rawId"

  override fun toString(): String = fullId

  companion object {

    /**
     * Creates a custom agent ID from a display name.
     * Combines sanitization with the custom agent type.
     *
     * Example: "My Agent" -> AcpCustomAgentId("my-agent")
     *
     * @param name The display name
     * @return Custom agent ID
     */
    fun fromName(name: String): AcpCustomAgentId = AcpCustomAgentId(AcpAgentId.sanitize(name))
  }
}


data class AcpRemoteAgentId(override val rawId: String) : AcpAgentId {
  companion object {
    fun fromName(name: String): AcpRemoteAgentId = AcpRemoteAgentId(AcpAgentId.sanitize(name))
  }
  override val fullId: String get() = "${AcpAgentId.REMOTE_PREFIX}$rawId"
}

/**
 * Represents a registry ACP agent ID (prefix: "acp.registry.").
 * Registry agents are loaded from the external agent registry.
 */
data class AcpRegistryAgentId(override val rawId: String) : AcpAgentId {
  override val fullId: String get() = "${AcpAgentId.REGISTRY_PREFIX}$rawId"

  override fun toString(): String = fullId

}
