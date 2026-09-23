// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "LiftReturnOrAssignment", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.InMemoryContentSource
import org.jetbrains.intellij.build.Source
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

enum class PatchOverwriteMode {
  TRUE,
  FALSE,
  IF_EQUAL,
}

class ModuleOutputPatcher {
  /**
   * The paths a module's jar carries that the module output does not hold, in the order they were stated.
   *
   * A patch says "this path is in the jar, and its bytes are not in the module output". An [InMemoryContentSource] holds
   * the text this build computed. The insertion order is the order the entries reach the jar, so it decides the jar's
   * bytes through `__index__`.
   */
  private val patches = ConcurrentHashMap<String, MutableMap<String, Source>>()

  fun patchModuleOutput(moduleName: String, path: String, content: String, overwrite: PatchOverwriteMode = PatchOverwriteMode.FALSE) {
    patchModuleOutput(moduleName = moduleName, path = path, content = content.encodeToByteArray(), overwrite = overwrite)
  }

  fun patchModuleOutput(moduleName: String, path: String, content: ByteArray, overwrite: Boolean) {
    patchModuleOutput(moduleName = moduleName, path = path, content = content, overwrite = if (overwrite) PatchOverwriteMode.TRUE else PatchOverwriteMode.FALSE)
  }

  fun patchModuleOutput(moduleName: String, path: String, content: ByteArray, overwrite: PatchOverwriteMode = PatchOverwriteMode.FALSE) {
    val pathToSource = patches.computeIfAbsent(moduleName) { Collections.synchronizedMap(LinkedHashMap()) }
    val source = InMemoryContentSource(path, content)
    if (overwrite == PatchOverwriteMode.TRUE) {
      val overwritten = pathToSource.put(path, source) != null
      Span.current().addEvent("patch module output", Attributes.of(
        AttributeKey.stringKey("module"), moduleName,
        AttributeKey.stringKey("path"), path,
        AttributeKey.booleanKey("overwrite"), true,
        AttributeKey.booleanKey("overwritten"), overwritten,
      ))
    }
    else {
      val existing = pathToSource.putIfAbsent(path, source)
      val span = Span.current()
      if (existing != null) {
        val existingData = (existing as? InMemoryContentSource)?.data
        if (overwrite != PatchOverwriteMode.IF_EQUAL && existingData?.contentEquals(content) != true) {
          span.addEvent("failed to patch because path is duplicated", Attributes.of(
            AttributeKey.stringKey("path"), path,
            AttributeKey.stringKey("oldContent"), existingData?.let { byteArrayToTraceStringValue(it) } ?: existing.toString(),
            AttributeKey.stringKey("newContent"), byteArrayToTraceStringValue(content),
          ))
          error("Patched file '$path' is already added for module $moduleName")
        }

        pathToSource.put(path, source)
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

  /** Every patched path of [moduleName], in the order it was stated. */
  internal fun getPatchedSources(moduleName: String): Map<String, Source> = patches.get(moduleName) ?: emptyMap()

  internal fun getPatchedModuleNames(): Set<String> = patches.keys.toSet()

  internal fun getPatchedContent(moduleName: String): Map<String, ByteArray> {
    val pathToSource = patches.get(moduleName) ?: return emptyMap()
    val result = LinkedHashMap<String, ByteArray>(pathToSource.size)
    for ((path, source) in pathToSource) {
      if (source is InMemoryContentSource) {
        result.put(path, source.data)
      }
    }
    return result
  }

  /** Whether [path] of [moduleName] is patched, whichever kind states it. */
  fun hasPatch(moduleName: String, path: String): Boolean = patches.get(moduleName)?.containsKey(path) == true

  /** How many paths of [moduleName] are patched, over both kinds. */
  fun patchCount(moduleName: String): Int = patches.get(moduleName)?.size ?: 0
}
