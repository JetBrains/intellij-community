// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "LiftReturnOrAssignment", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.FileSource
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
   * A patch says "this path is in the jar, and its bytes are not in the module output". It does not say where the bytes
   * come from, and two kinds do. An [InMemoryContentSource] holds text this build computed that no file holds. A
   * [FileSource] names a declared file - a produced plugin descriptor is that kind, and the executed recipe then states
   * the file's label rather than reporting that code made the bytes.
   *
   * **One ordered map, and not one per kind.** The insertion order is the order the entries reach the jar, so it decides
   * the jar's bytes through `__index__`. Two maps re-order a module that states both kinds: it moved
   * `META-INF/plugin.xml` behind the Kotlin plugin's other patched entries and changed that jar, while every entry in it
   * stayed byte-identical.
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

  /**
   * Patches [path] of [moduleName] with the bytes a declared file holds.
   *
   * `IF_EQUAL` semantics, which is what the one caller needs: an OS-specific plugin is laid out several times, and every
   * pass states the same file. Equality is by file, because the file is one manifest entry resolved to one path, so two
   * states of it are the same bytes by construction.
   */
  fun patchModuleOutputWithFile(moduleName: String, path: String, source: FileSource) {
    require(source.relativePath == path) {
      "FileSource must state the path it patches (path=$path, source=$source)"
    }

    val pathToSource = patches.computeIfAbsent(moduleName) { Collections.synchronizedMap(LinkedHashMap()) }
    val existing = pathToSource.putIfAbsent(path, source)
    require(existing == null || existing == source) {
      "Patched file '$path' of module $moduleName is already stated (existing=$existing, new=$source)"
    }

    Span.current().addEvent("patch module output with a file", Attributes.of(
      AttributeKey.stringKey("module"), moduleName,
      AttributeKey.stringKey("path"), path,
      AttributeKey.stringKey("file"), source.file.toString(),
    ))
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
