// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "LiftReturnOrAssignment", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap

enum class PatchOverwriteMode {
  TRUE,
  FALSE,
  IF_EQUAL,
}

class ModuleOutputPatcher {
  private val patches = ConcurrentHashMap<String, MutableMap<String, ByteArray>>()

  fun patchModuleOutput(moduleName: String, path: String, content: String, overwrite: PatchOverwriteMode = PatchOverwriteMode.FALSE) {
    patchModuleOutput(moduleName = moduleName, path = path, content = content.toByteArray(StandardCharsets.UTF_8), overwrite = overwrite)
  }

  fun patchModuleOutput(moduleName: String, path: String, content: ByteArray, overwrite: Boolean) {
    patchModuleOutput(moduleName = moduleName, path = path, content = content, overwrite = if (overwrite) PatchOverwriteMode.TRUE else PatchOverwriteMode.FALSE)
  }

  fun patchModuleOutput(moduleName: String, path: String, content: ByteArray, overwrite: PatchOverwriteMode = PatchOverwriteMode.FALSE) {
    val pathToData = patches.computeIfAbsent(moduleName) { Collections.synchronizedMap(LinkedHashMap()) }
    if (overwrite == PatchOverwriteMode.TRUE) {
      val overwritten = pathToData.put(path, content) != null
      Span.current().addEvent("patch module output", Attributes.of(
        AttributeKey.stringKey("module"), moduleName,
        AttributeKey.stringKey("path"), path,
        AttributeKey.booleanKey("overwrite"), true,
        AttributeKey.booleanKey("overwritten"), overwritten,
      ))
    }
    else {
      val existing = pathToData.putIfAbsent(path, content)
      val span = Span.current()
      if (existing != null) {
        if (overwrite != PatchOverwriteMode.IF_EQUAL && !existing.contentEquals(content)) {
          span.addEvent("failed to patch because path is duplicated", Attributes.of(
            AttributeKey.stringKey("path"), path,
            AttributeKey.stringKey("oldContent"), byteArrayToTraceStringValue(existing),
            AttributeKey.stringKey("newContent"), byteArrayToTraceStringValue(content),
          ))
          error("Patched directory $path is already added for module $moduleName")
        }

        pathToData.put(path, content)
      }

      span.addEvent("patch module output", Attributes.of(
        AttributeKey.stringKey("module"), moduleName,
        AttributeKey.stringKey("path"), path,
      ))
    }
  }

  private fun byteArrayToTraceStringValue(value: ByteArray): String {
    try {
      return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(value)).toString()
    }
    catch (_: CharacterCodingException) {
      return Base64.getMimeEncoder().encodeToString(value)
    }
  }

  fun getPatchedPluginXmlIfExists(moduleName: String): ByteArray? {
    return patches.get(moduleName)?.entries?.firstOrNull { it.key == "META-INF/plugin.xml" }?.value
  }

  fun getPatchedPluginXml(moduleName: String): ByteArray {
    return patches.get(moduleName)?.entries?.firstOrNull { it.key == "META-INF/plugin.xml" }?.value
           ?: error("patched plugin.xml not found for $moduleName module")
  }

  internal fun getPatchedContent(moduleName: String): Map<String, ByteArray> = patches.get(moduleName) ?: emptyMap()
}
