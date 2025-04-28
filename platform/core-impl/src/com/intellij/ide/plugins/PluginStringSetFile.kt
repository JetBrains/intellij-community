// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.util.*
import kotlin.streams.asSequence

/**
 * DO NOT USE outside the plugins subsystem code, API can be changed arbitrarily without a notice.
 *
 * Persists a set of trimmed, non-empty strings to a file on disk.
 */
@ApiStatus.Internal
object PluginStringSetFile {
  @Synchronized
  @Throws(IOException::class)
  fun write(path: Path, strings: Set<String>) {
    NioFiles.createDirectories(path.parent)
    // TODO: TreeSet demands comparable, probably we can drop Comparable on PluginId if we drop this usage
    //  wait, it's String, not PluginId

    Files.write(path, TreeSet(strings))
  }

  @Synchronized
  fun writeSafe(path: Path, strings: Set<String>, logger: Logger): Boolean {
    try {
      write(path, strings)
      return true
    } catch (e: IOException) {
      logger.warn("failed to write plugin strings to $path", e)
      return false
    }
  }

  @Synchronized
  fun writeIdsSafe(path: Path, ids: Set<PluginId>, logger: Logger): Boolean {
    try {
      write(path, ids.mapTo(mutableSetOf()) { it.idString })
      return true
    } catch (e: IOException) {
      logger.warn("failed to write plugin strings to $path", e)
      return false
    }
  }

  @Synchronized
  @Throws(IOException::class)
  fun append(path: Path, strings: Set<String>) {
    NioFiles.createDirectories(path.parent)
    Files.write(path, TreeSet(strings), CREATE, WRITE, APPEND)
  }

  @Synchronized
  fun appendSafe(path: Path, strings: Set<String>, logger: Logger): Boolean {
    try {
      append(path, strings)
      return true
    } catch (e: IOException) {
      logger.warn("failed to append plugin strings to $path", e)
      return false
    }
  }

  @Synchronized
  fun appendIdsSafe(path: Path, ids: Set<PluginId>, logger: Logger): Boolean {
    try {
      append(path, ids.mapTo(mutableSetOf()) { it.idString })
      return true
    } catch (e: IOException) {
      logger.warn("failed to append plugin strings to $path", e)
      return false
    }
  }

  @Synchronized
  fun consumeSafe(path: Path, logger: Logger): Set<String> {
    return try {
      val ids = read(path)
      if (!ids.isEmpty()) {
        Files.delete(path) // TODO may throw, but in that case we'll return emptySet, huh?
      }
      ids
    }
    catch (e: IOException) {
      logger.error(path.toString(), e)
      emptySet()
    }
  }

  @Synchronized
  fun consumeIdsSafe(path: Path, logger: Logger): Set<PluginId> = consumeSafe(path, logger).mapTo(LinkedHashSet(), PluginId::getId)

  @Synchronized
  fun readSafe(path: Path, log: Logger): Set<String> {
    return try {
      read(path)
    }
    catch (e: IOException) {
      log.warn("Unable to read plugin string set from: $path", e)
      emptySet()
    }
  }

  @Synchronized
  fun readIdsSafe(path: Path, log: Logger): Set<PluginId> = readSafe(path, log).mapTo(LinkedHashSet(), PluginId::getId)

  @Synchronized
  @Throws(IOException::class)
  fun read(path: Path): Set<String> {
    return try {
      Files.lines(path).use { lines ->
        lines.asSequence()
          .map(String::trim)
          .filter { line -> !line.isEmpty() }
          .toSet()
      }
    }
    catch (_: NoSuchFileException) {
      emptySet()
    }
  }
}