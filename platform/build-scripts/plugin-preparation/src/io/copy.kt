// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.Predicate

/**
 * Always passes [StandardCopyOption.COPY_ATTRIBUTES], and not only to carry the mode over: since JDK 20 that option is
 * what makes the JDK attempt the host's copy-on-write path - Apple's `clonefile` on APFS, `copy_file_range` on Linux,
 * which reflinks on Btrfs and reflink-enabled XFS. Without it the same call writes real bytes: measured on JBR 25.0.4
 * and APFS, a 220 MB file takes 0.4 ms and no additional space with the option, 31 ms and its full size without.
 * [StandardCopyOption.REPLACE_EXISTING] does not cost the clone; a missing [StandardCopyOption.COPY_ATTRIBUTES] does.
 *
 * It is an implementation optimization rather than a guarantee - a cross-volume copy, or any filesystem without
 * copy-on-write, falls back to writing bytes - so nothing may depend on the copy being cheap, only benefit from it.
 */
@Internal
fun doCopyFile(file: Path, target: Path, targetDir: Path, overwrite: Boolean) {
  Files.createDirectories(targetDir)
  if (overwrite) {
    Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
  }
  else {
    Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES)
  }
}

@Internal
fun doCopyDirectory(
  sourceDir: Path,
  targetDir: Path,
  overwrite: Boolean,
  dirFilter: Predicate<Path>? = null,
  fileFilter: Predicate<Path>? = null,
): List<Path> {
  Files.createDirectories(targetDir)
  val dirFilter = dirFilter ?: Predicate { true }
  val fileFilter = fileFilter ?: Predicate { true }
  val visitor = CopyDirectoryVisitor(sourceDir, targetDir, dirFilter, fileFilter, overwrite)
  Files.walkFileTree(sourceDir, visitor)
  return visitor.copiedFiles
}

private class CopyDirectoryVisitor(
  private val sourceDir: Path,
  private val targetDir: Path,
  private val dirFilter: Predicate<Path>,
  private val fileFilter: Predicate<Path>,
  private val overwrite: Boolean,
) : SimpleFileVisitor<Path>() {
  private val sourceToTargetFile: (Path) -> Path

  /** The files this visitor wrote, in visit order. */
  @JvmField val copiedFiles: MutableList<Path> = mutableListOf()

  init {
    val isTheSameFileStore = Files.getFileStore(sourceDir) == Files.getFileStore(targetDir)
    // support copying to ZipFS
    if (isTheSameFileStore) {
      sourceToTargetFile = { targetDir.resolve(sourceDir.relativize(it)) }
    }
    else {
      sourceToTargetFile = { targetDir.resolve(sourceDir.relativize(it).toString()) }
    }
  }

  override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
    if (!dirFilter.test(directory)) {
      return FileVisitResult.SKIP_SUBTREE
    }

    try {
      Files.createDirectory(sourceToTargetFile(directory))
    }
    catch (_: FileAlreadyExistsException) { }
    return FileVisitResult.CONTINUE
  }

  override fun visitFile(sourceFile: Path, attributes: BasicFileAttributes): FileVisitResult {
    if (!fileFilter.test(sourceFile)) {
      return FileVisitResult.CONTINUE
    }

    val targetFile = sourceToTargetFile(sourceFile)
    if (overwrite) {
      Files.copy(sourceFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS)
    }
    else {
      Files.copy(sourceFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
    }
    copiedFiles.add(targetFile)
    return FileVisitResult.CONTINUE
  }
}
