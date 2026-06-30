// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.DosFileAttributes
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.util.logging.Logger
import kotlin.collections.plus
import kotlin.io.path.fileAttributesViewOrNull
import kotlin.io.path.pathString

@ApiStatus.Experimental
sealed interface EelFileTransferAttributesStrategy {
  object Skip : EelFileTransferAttributesStrategy

  interface SourceAware : EelFileTransferAttributesStrategy {
    fun handleFileAttributes(source: Path, target: Path, sourceAttrs: BasicFileAttributes)
  }

  object Copy : SourceAware {
    override fun handleFileAttributes(source: Path, target: Path, sourceAttrs: BasicFileAttributes) {
      copyAttributes(sourceAttrs, target, sourcePathString = source.pathString)
    }
  }

  data class RequirePosixPermissions(val requiredPermissions: Set<PosixFilePermission>) : SourceAware {
    override fun handleFileAttributes(source: Path, target: Path, sourceAttrs: BasicFileAttributes) {
      copyAttributes(sourceAttrs, target, sourcePathString = source.pathString,
                     requirePosixPermissions = requiredPermissions)
    }
  }

  companion object {
    @JvmStatic
    fun copyWithRequiredPosixPermissions(vararg requiredPermissions: PosixFilePermission): RequirePosixPermissions =
      RequirePosixPermissions(setOf(elements = requiredPermissions))
  }
}

private val LOG = Logger.getLogger(EelFileTransferAttributesStrategy::class.java.name)

/**
 * Copies file attributes from a source file to the target path, ensuring compatibility with different
 * file systems such as POSIX and Windows.
 *
 * @param sourceAttrs             The file attributes of the source file from which the attributes will be copied.
 * @param target                  The target path where the attributes will be applied.
 * @param sourcePathString        The string representation of the source path, used for logging purposes.
 * @param requirePosixPermissions A set of additional POSIX file permissions required to be merged
 *                                into the copied attributes.
 */
private fun copyAttributes(
  sourceAttrs: BasicFileAttributes,
  target: Path,
  sourcePathString: String,
  requirePosixPermissions: Set<PosixFilePermission> = emptySet(),
) {
  fun <T> Result<T>.logIOExceptionOrThrow(fileAttributeViewName: String): Result<T> =
    handleIOExceptionOrThrow { LOG.info("Failed to copy $fileAttributeViewName file attributes from $sourcePathString to $target: $it") }

  target.fileAttributesViewOrNull<PosixFileAttributeView>(LinkOption.NOFOLLOW_LINKS)?.let { posixView ->
    runCatching { copyPosixOnlyFileAttributes(sourceAttrs, posixView, requirePosixPermissions) }
      .logIOExceptionOrThrow(fileAttributeViewName = "Posix")
  }

  target.fileAttributesViewOrNull<DosFileAttributeView>(LinkOption.NOFOLLOW_LINKS)?.let { dosView ->
    runCatching { copyDosOnlyFileAttributes(sourceAttrs, dosView) }
      .logIOExceptionOrThrow(fileAttributeViewName = "Windows")
  }

  target.fileAttributesViewOrNull<BasicFileAttributeView>(LinkOption.NOFOLLOW_LINKS)?.let { basicView ->
    runCatching { copyBasicFileAttributes(sourceAttrs, basicView) }
      .logIOExceptionOrThrow(fileAttributeViewName = "basic")
  }
}

private fun copyPosixOnlyFileAttributes(
  from: BasicFileAttributes,
  to: PosixFileAttributeView,
  requirePermissions: Set<PosixFilePermission> = emptySet(),
) {
  if (from is PosixFileAttributes) {
    // TODO It's ineffective for IjentNioFS, because there are 6 consequential system calls.
    to.setPermissions(from.permissions() + requirePermissions)
    if (to is PosixFileAttributes) {
      runCatching<UnsupportedOperationException>(
        { to.owner = from.owner() },
        { to.setGroup(from.group()) }
      )
    }
  }
  else {
    if (requirePermissions.isNotEmpty()) {
      to.setPermissions(to.readAttributes().permissions() + requirePermissions)
    }
  }
}

private fun copyDosOnlyFileAttributes(from: BasicFileAttributes, to: DosFileAttributeView) {
  if (from is DosFileAttributes) {
    to.setHidden(from.isHidden)
    to.setSystem(from.isSystem)
    to.setArchive(from.isArchive)
    to.setReadOnly(from.isReadOnly)
  }
  copyBasicFileAttributes(from, to)
}

private fun copyBasicFileAttributes(from: BasicFileAttributes, to: BasicFileAttributeView) {
  to.setTimes(
    from.lastModifiedTime(),
    from.lastAccessTime(),
    from.creationTime(),
  )
}

private inline fun <T> Result<T>.handleIOExceptionOrThrow(action: (exception: IOException) -> Unit): Result<T> =
  onFailure { if (it is IOException) action(it) else throw it }

private inline fun <reified T : Throwable> runCatching(vararg blocks: () -> Unit) {
  blocks.forEach {
    try {
      it()
    }
    catch (t: Throwable) {
      if (!T::class.isInstance(t)) {
        throw t
      }
    }
  }
}
