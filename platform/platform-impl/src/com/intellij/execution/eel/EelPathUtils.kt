// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import kotlin.io.path.createDirectories
import kotlin.io.path.readAttributes
import kotlin.io.path.relativeTo

@ApiStatus.Internal
object EelPathUtils {
  private val LOG = com.intellij.openapi.diagnostic.logger<EelPathUtils>()

  fun walkingTransfer(sourceRoot: Path, targetRoot: Path, removeSource: Boolean) {
    val sourceStack = ArrayDeque<Path>()
    sourceStack.add(sourceRoot)

    var lastDirectory: Path? = null

    while (true) {
      val source = try {
        sourceStack.removeLast()
      }
      catch (_: NoSuchElementException) {
        break
      }

      while (removeSource && lastDirectory != null && lastDirectory != sourceRoot && source.parent != lastDirectory) {
        Files.delete(lastDirectory)
        lastDirectory = lastDirectory.parent
      }

      val stat = BasicFileAttributesHolder2.getAttributesFromHolder(source) ?: source.readAttributes(LinkOption.NOFOLLOW_LINKS)

      // WindowsPath doesn't support resolve() from paths of different class.
      val target = source.relativeTo(sourceRoot).fold(targetRoot) { parent, file ->
        parent.resolve(file.toString())
      }

      when {
        stat.isDirectory -> {
          lastDirectory = source
          try {
            target.createDirectories()
          }
          catch (err: FileAlreadyExistsException) {
            if (!Files.isDirectory(target)) {
              throw err
            }
          }
          source.fileSystem.provider().newDirectoryStream(source, BasicFileAttributesHolder2.FetchAttributesFilter.ACCEPT_ALL).use { children ->
            sourceStack.addAll(children.toList().asReversed())
          }
        }

        stat.isRegularFile -> {
          Files.newInputStream(source, READ).use { reader ->
            Files.newOutputStream(target, CREATE, TRUNCATE_EXISTING, WRITE).use { writer ->
              reader.copyTo(writer, bufferSize = 4 * 1024 * 1024)
            }
          }
          if (removeSource) {
            Files.delete(source)
          }
        }

        else -> {
          LOG.info("Not copying $source to $target because the source file is neither a regular file nor a directory")
        }
      }
    }

    while (removeSource && lastDirectory != null && lastDirectory != sourceRoot) {
      Files.delete(lastDirectory)
      lastDirectory = lastDirectory.parent
    }
  }
}