// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IntelliJThreadDumpMetadata {
  const val META_DATA_MARKER: String = "============ INTELLIJ META_DATA ============"

  /**
   * Any change to the serialized metadata payload ([MetadataPayload]) must bump this version.
   */
  private const val CURRENT_FORMAT_VERSION: Int = 1
  private val LOG = logger<IntelliJThreadDumpMetadata>()
  private val metadataJson = Json {
    prettyPrint = true
    explicitNulls = true
    ignoreUnknownKeys = true
  }

  /**
   * Serializes metadata that supplements the plain-text thread dump body.
   */
  fun serialize(metadata: ThreadDumpMetadata): String {
    val payload = MetadataPayload(
      version = CURRENT_FORMAT_VERSION,
      threadLinks = metadata.threadLinks,
      containers = metadata.containers,
    )
    return metadataJson.encodeToString(payload)
  }

  /**
   * Parses the metadata footer and returns `null` when no valid JSON payload is found.
   */
  fun parse(rawMetadata: String): ThreadDumpMetadata? {
    val jsonStart = rawMetadata.indexOf('{')
    if (jsonStart < 0) {
      if (rawMetadata.isNotBlank()) {
        LOG.warn("Failed to parse IntelliJ thread dump metadata: JSON payload was not found")
      }
      return null
    }
    val metadataText = rawMetadata.substring(jsonStart).trim()
    return runCatching { metadataJson.decodeFromString<MetadataPayload>(metadataText) }
      .onFailure { LOG.warn("Failed to parse IntelliJ thread dump metadata", it) }
      .getOrNull()
      ?.let { payload ->
        ThreadDumpMetadata(
          threadLinks = payload.threadLinks,
          containers = payload.containers,
        )
      }
  }

  data class ThreadDumpMetadata(
    val threadLinks: List<TreeLink>,
    val containers: List<Container>,
  )

  @Serializable
  data class TreeLink(
    @SerialName("tree_id")
    val treeId: Long,
    @SerialName("parent_tree_id")
    val parentTreeId: Long?,
  )

  @Serializable
  data class Container(
    val name: String,
    @SerialName("tree_id")
    val treeId: Long,
  )

  @Serializable
  private data class MetadataPayload(
    val version: Int,
    @SerialName("tree_links")
    val threadLinks: List<TreeLink> = emptyList(),
    val containers: List<Container> = emptyList(),
  )
}

@ApiStatus.Internal
data class SeparatedThreadDumpText(
  val firstLine: @NlsSafe String,
  val body: @NlsSafe String,
)

@ApiStatus.Internal
fun splitFirstLineAndBody(text: String): SeparatedThreadDumpText {
  val normalized = StringUtil.convertLineSeparators(text)
  if (normalized.isEmpty()) {
    return SeparatedThreadDumpText("", "")
  }

  val firstLineEnd = normalized.indexOf('\n')
  return if (firstLineEnd < 0) {
    SeparatedThreadDumpText(normalized, "")
  }
  else {
    SeparatedThreadDumpText(
      firstLine = normalized.substring(0, firstLineEnd),
      body = normalized.substring(firstLineEnd + 1),
    )
  }
}

@ApiStatus.Internal
fun joinFirstLineAndBody(firstLine: String, body: String): String {
  return when {
    firstLine.isEmpty() -> body
    body.isEmpty() -> firstLine
    else -> "$firstLine\n$body"
  }
}
