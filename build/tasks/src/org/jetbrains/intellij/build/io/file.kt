// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.util.*
import java.util.function.Predicate

internal val RW_CREATE_NEW = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.READ,
                                        StandardOpenOption.CREATE_NEW)
internal val W_CREATE_NEW = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)

internal val isWindows = !System.getProperty("os.name").startsWith("windows", ignoreCase = true)

fun copyDir(sourceDir: Path, targetDir: Path, dirFilter: Predicate<Path>? = null, fileFilter: Predicate<Path>? = null) {
  Files.createDirectories(targetDir)
  Files.walkFileTree(sourceDir, CopyDirectoryVisitor(
    sourceDir,
    targetDir,
    dirFilter = dirFilter ?: Predicate { true },
    fileFilter = fileFilter ?: Predicate { true },
  ))
}

internal inline fun writeNewFile(file: Path, task: (FileChannel) -> Unit) {
  Files.createDirectories(file.parent)
  FileChannel.open(file, W_CREATE_NEW).use {
    task(it)
  }
}

private class CopyDirectoryVisitor(private val sourceDir: Path,
                                   private val targetDir: Path,
                                   private val dirFilter: Predicate<Path>,
                                   private val fileFilter: Predicate<Path>) : SimpleFileVisitor<Path>() {
  private val useHardlink: Boolean
  private val sourceToTargetFile: (Path) -> Path

  init {
    val isTheSameFileStore = Files.getFileStore(sourceDir) == Files.getFileStore(targetDir)
    useHardlink = !isWindows && isTheSameFileStore
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
    catch (ignore: FileAlreadyExistsException) {
    }
    return FileVisitResult.CONTINUE
  }

  override fun visitFile(sourceFile: Path, attributes: BasicFileAttributes): FileVisitResult {
    if (!fileFilter.test(sourceFile)) {
      return FileVisitResult.CONTINUE
    }

    val targetFile = sourceToTargetFile(sourceFile)

    if (useHardlink) {
      Files.createLink(targetFile, sourceFile)
    }
    else {
      Files.copy(sourceFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES)
    }
    return FileVisitResult.CONTINUE
  }
}

internal fun deleteDir(startDir: Path) {
  Files.walkFileTree(startDir, object : SimpleFileVisitor<Path>() {
    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      deleteFile(file)
      return FileVisitResult.CONTINUE
    }

    override fun postVisitDirectory(dir: Path, exception: IOException?): FileVisitResult {
      if (exception != null) {
        throw exception
      }

      Files.deleteIfExists(dir)
      return FileVisitResult.CONTINUE
    }
  })
}

private fun deleteFile(file: Path) {
  // repeated delete is required for bad OS like Windows
  val maxAttemptCount = 10
  var attemptCount = 0
  while (true) {
    try {
      Files.deleteIfExists(file)
      return
    }
    catch (e: IOException) {
      if (++attemptCount == maxAttemptCount) {
        throw e
      }

      if (e is AccessDeniedException && isWindows) {
        val view = Files.getFileAttributeView(file, DosFileAttributeView::class.java)
        if (view != null && view.readAttributes().isReadOnly) {
          view.setReadOnly(false)
        }
      }

      try {
        Thread.sleep(10)
      }
      catch (ignored: InterruptedException) {
        throw e
      }
    }
  }
}

internal inline fun transformFile(file: Path, task: (tempFile: Path) -> Unit) {
  val tempFile = file.parent.resolve("${file.fileName}.tmp")
  try {
    task(tempFile)
    Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING)
  }
  finally {
    Files.deleteIfExists(tempFile)
  }
}