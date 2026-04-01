// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.threadDumpParser.ID_KEY
import com.intellij.threadDumpParser.PARENT_ID_KEY
import com.intellij.threadDumpParser.TYPE_KEY
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IntelliJThreadDumpMetadata {
  const val CONTAINER_TYPE: String = "container"
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

/**
 * Serializes one exported thread-dump item.
 *
 * [itemHeader] stays visible in the first line, while [id], [parentId], [type], and [additionalMetadata]
 * are encoded as inline metadata. The metadata is built as a JSON object and then written with `[` and `]`
 * instead of the outer `{` and `}` to match the older [com.intellij.threadDumpParser.ThreadDumpParser] capabilities.
 */
@ApiStatus.Internal
fun serializeThreadDumpItem(
  itemHeader: String,
  stackTraceBody: String,
  id: Long?,
  parentId: Long?,
  type: String? = null,
  additionalMetadata: Map<String, String> = emptyMap(),
): String {
  val trimmedItemHeader = itemHeader.trimEnd()
  val metadataObject = buildMetadataObject(type, id, parentId, additionalMetadata)
  val firstLine = if (metadataObject == null) {
    trimmedItemHeader
  }
  else {
    "$trimmedItemHeader ${metadataObject.toBracketedMetadataBlock()}"
  }
  return joinFirstLineAndBody(firstLine, stackTraceBody)
}

private fun buildMetadataObject(
  type: String?,
  id: Long?,
  parentId: Long?,
  additionalMetadata: Map<String, String>,
): JsonObject? {
  val values = mutableMapOf<String, JsonPrimitive>()
  type?.let {
    values[TYPE_KEY] = JsonPrimitive(it)
  }
  id?.let {
    values[ID_KEY] = JsonPrimitive(it)
  }
  parentId?.let {
    values[PARENT_ID_KEY] = JsonPrimitive(it)
  }
  additionalMetadata.forEach { (key, value) ->
    values[key] = JsonPrimitive(value)
  }
  return values.takeIf { it.isNotEmpty() }?.let(::JsonObject)
}

private fun JsonObject.toBracketedMetadataBlock(): String {
  val objectText = toString()
  return "[${objectText.substring(1, objectText.length - 1)}]"
}
