// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

@CompileStatic
final class ModuleOutputPatcher {
  final Map<String, ConcurrentLinkedQueue<Path>> patchDirs = new ConcurrentHashMap<>()
  final Map<String, Map<String, byte[]>> patches = new ConcurrentHashMap<>()

  void patchModuleOutput(@NotNull String moduleName, @NotNull String path, @NotNull String content, boolean overwrite = false) {
    patchModuleOutput(moduleName, path, content.getBytes(StandardCharsets.UTF_8), overwrite)
  }

  void patchModuleOutput(@NotNull String moduleName, @NotNull String path, @NotNull byte[] content, boolean overwrite = false) {
    Map<String, byte[]> pathToData = patches.computeIfAbsent(moduleName, { Collections.synchronizedMap(new LinkedHashMap<>()) })
    if (overwrite) {
      boolean overwritten = pathToData.put(path, content) != null
      Span.current().addEvent("patch module output", Attributes.of(
        AttributeKey.stringKey("module"), moduleName,
        AttributeKey.stringKey("path"), path,
        AttributeKey.booleanKey("overwrite"), true,
        AttributeKey.booleanKey("overwritten"), overwritten,
        ))
    }
    else {
      byte[] existing = pathToData.putIfAbsent(path, content)
      Span span = Span.current()
      if (existing != null) {
        span.addEvent("failed to patch because path is duplicated", Attributes.of(
          AttributeKey.stringKey("path"), path,
          AttributeKey.stringKey("oldContent"), byteArrayToTraceStringValue(existing),
          AttributeKey.stringKey("newContent"), byteArrayToTraceStringValue(content),
        ))
        throw new IllegalStateException("Patched directory $path is already added for module $moduleName")
      }

      span.addEvent("patch module output", Attributes.of(
        AttributeKey.stringKey("module"), moduleName,
        AttributeKey.stringKey("path"), path,
        ))
    }
  }

  private static String byteArrayToTraceStringValue(byte[] value) {
    try {
      return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(value)).toString()
    }
    catch (CharacterCodingException ignore) {
      return Base64.mimeEncoder.encodeToString(value)
    }
  }

  @Nullable
  String getPatchedPluginXmlIfExists(@NotNull String moduleName) {
    byte[] result = patches.get(moduleName)?.find { it.getKey() == "META-INF/plugin.xml" }?.value
    return result == null ? null : new String(result, StandardCharsets.UTF_8)
  }

  @NotNull
  byte[] getPatchedPluginXml(@NotNull String moduleName) {
    byte[] result = patches.get(moduleName)?.find { it.getKey() == "META-INF/plugin.xml" }?.value
    if (result == null) {
      throw new IllegalStateException("patched plugin.xml not found for $moduleName module")
    }
    return result
  }

  /**
   * Contents of {@code pathToDirectoryWithPatchedFiles} will be used to patch the module output.
   */
  void patchModuleOutput(String moduleName, Path pathToDirectoryWithPatchedFiles) {
    Collection<Path> list = patchDirs.computeIfAbsent(moduleName, { new CopyOnWriteArrayList<>() })
    if (list.contains(pathToDirectoryWithPatchedFiles)) {
      throw new IllegalStateException("Patched directory $pathToDirectoryWithPatchedFiles is already added for module $moduleName")
    }
    list.add(pathToDirectoryWithPatchedFiles)
  }

  @NotNull
  Collection<Path> getPatchedDir(String moduleName) {
    return patchDirs.get(moduleName) ?: Collections.<Path> emptyList()
  }

  @NotNull
  Map<String, byte[]> getPatchedContent(String moduleName) {
    return patches.get(moduleName) ?: Collections.<String, byte[]>emptyMap()
  }
}
