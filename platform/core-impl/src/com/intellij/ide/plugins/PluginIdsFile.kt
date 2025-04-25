// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.streams.asSequence

@ApiStatus.Internal
object PluginIdsFile {
  @Synchronized
  fun readSafe(path: Path, log: Logger): Set<PluginId> {
    return try {
      read(path)
    }
    catch (e: IOException) {
      log.warn("Unable to read plugin id list from: $path", e)
      emptySet()
    }
  }

  @Synchronized
  @Throws(IOException::class)
  fun read(path: Path): Set<PluginId> {
    return try {
      Files.lines(path).use { lines ->
        lines.asSequence()
          .map(String::trim)
          .filter { line -> !line.isEmpty() }
          .map { idString -> PluginId.getId(idString) }
          .toSet()
      }
    }
    catch (_: NoSuchFileException) {
      emptySet()
    }
  }
}