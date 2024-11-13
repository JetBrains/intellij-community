// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.*
import kotlin.io.path.createDirectories
import kotlin.io.path.fileAttributesView
import kotlin.io.path.relativeTo

@ApiStatus.Internal
object EelPathUtils {
  private val LOG = com.intellij.openapi.diagnostic.logger<EelPathUtils>()

  fun walkingTransfer(sourceRoot: Path, targetRoot: Path, removeSource: Boolean, copyAttributes: Boolean) {
    val sourceStack = ArrayDeque<Path>()
    sourceStack.add(sourceRoot)

    class LastDirectoryInfo(
      val parent: LastDirectoryInfo?,
      val source: Path,
      val target: Path,
      val sourceAttrs: BasicFileAttributes,
    )
    var lastDirectory: LastDirectoryInfo? = null

    while (true) {
      val source = try {
        sourceStack.removeLast()
      }
      catch (_: NoSuchElementException) {
        break
      }

      if (removeSource || copyAttributes) {
        while (lastDirectory != null && lastDirectory.source != sourceRoot && source.parent != lastDirectory.source) {
          if (removeSource) {
            Files.delete(lastDirectory.source)
          }
          if (copyAttributes) {
            copyAttributes(lastDirectory.source, lastDirectory.target, lastDirectory.sourceAttrs)
          }
          lastDirectory = lastDirectory.parent
        }
      }

      // WindowsPath doesn't support resolve() from paths of different class.
      val target = source.relativeTo(sourceRoot).fold(targetRoot) { parent, file ->
        parent.resolve(file.toString())
      }

      val sourceAttrs: BasicFileAttributes = readSourceAttrs(source, target, withExtendedAttributes = copyAttributes)

      when {
        sourceAttrs.isDirectory -> {
          lastDirectory = LastDirectoryInfo(lastDirectory, source, target, sourceAttrs)
          try {
            target.createDirectories()
          }
          catch (err: FileAlreadyExistsException) {
            if (!Files.isDirectory(target)) {
              throw err
            }
          }
          source.fileSystem.provider().newDirectoryStream(source, { true }).use { children ->
            sourceStack.addAll(children.toList().asReversed())
          }
        }

        sourceAttrs.isRegularFile -> {
          Files.newInputStream(source, READ).use { reader ->
            Files.newOutputStream(target, CREATE, TRUNCATE_EXISTING, WRITE).use { writer ->
              reader.copyTo(writer, bufferSize = 4 * 1024 * 1024)
            }
          }
          if (removeSource) {
            Files.delete(source)
          }
          if (copyAttributes) {
            copyAttributes(source, target, sourceAttrs)
          }
        }

        sourceAttrs.isSymbolicLink -> {
          Files.copy(source, target, LinkOption.NOFOLLOW_LINKS)
          if (copyAttributes) {
            copyAttributes(source, target, sourceAttrs)
          }
          if (removeSource) {
            Files.delete(source)
          }
          if (copyAttributes) {
            copyAttributes(source, target, sourceAttrs)
          }
        }

        else -> {
          LOG.info("Not copying $source to $target because the source file is neither a regular file nor a directory")
          if (removeSource) {
            Files.delete(source)
          }
        }
      }
    }

    if (removeSource || copyAttributes) {
      while (lastDirectory != null && lastDirectory.source != sourceRoot) {
        if (removeSource) {
          Files.delete(lastDirectory.source)
        }
        if (copyAttributes) {
          copyAttributes(lastDirectory.source, lastDirectory.target, lastDirectory.sourceAttrs)
        }
        lastDirectory = lastDirectory.parent
      }
    }
  }

  private fun readSourceAttrs(
    source: Path,
    target: Path,
    withExtendedAttributes: Boolean,
  ): BasicFileAttributes {
    val attributesIntersection =
      if (withExtendedAttributes)
        source.fileSystem.supportedFileAttributeViews() intersect target.fileSystem.supportedFileAttributeViews()
      else
        setOf()

    val osSpecific =
      try {
        when {
          "posix" in attributesIntersection ->
            source.fileAttributesView<PosixFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()

          "dos" in attributesIntersection ->
            source.fileAttributesView<DosFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()

          else -> null
        }
      }
      catch (err: UnsupportedOperationException) {
        LOG.info("Failed to read os-specific file attributes from $source", err)
        null
      }
    return osSpecific ?: source.fileAttributesView<BasicFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()
  }

  private fun copyAttributes(source: Path, target: Path, sourceAttrs: BasicFileAttributes) {
    if (sourceAttrs is PosixFileAttributes) {
      try {
        val targetView = Files.getFileAttributeView(target, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
        if (targetView != null) {
          // TODO It's ineffective for IjentNioFS, because there are 6 consequential system calls.
          targetView.setPermissions(sourceAttrs.permissions())
          targetView.setOwner(sourceAttrs.owner())
          targetView.setGroup(sourceAttrs.group())
        }
      }
      catch (err: IOException) {
        LOG.info("Failed to copy Posix file attributes from $source to $target: $err")
      }
    }

    if (sourceAttrs is DosFileAttributes) {
      try {
        val targetView = Files.getFileAttributeView(target, DosFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
        if (targetView != null) {
          targetView.setHidden(sourceAttrs.isHidden)
          targetView.setSystem(sourceAttrs.isSystem)
          targetView.setArchive(sourceAttrs.isArchive)
          targetView.setReadOnly(sourceAttrs.isReadOnly)
        }
      }
      catch (err: IOException) {
        LOG.info("Failed to copy Windows file attributes from $source to $target: $err")
      }
    }

    try {
      Files.getFileAttributeView(target, BasicFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS).setTimes(
        sourceAttrs.lastModifiedTime(),
        sourceAttrs.lastAccessTime(),
        sourceAttrs.creationTime(),
      )
    }
    catch (err: IOException) {
      LOG.info("Failed to copy basic file attributes from $source to $target: $err")
    }
  }
}