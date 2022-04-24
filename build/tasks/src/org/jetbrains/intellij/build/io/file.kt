// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.function.Predicate

internal val W_CREATE_NEW = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)

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
  private val sourceToTargetFile: (Path) -> Path

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
    catch (ignore: FileAlreadyExistsException) {
    }
    return FileVisitResult.CONTINUE
  }

  override fun visitFile(sourceFile: Path, attributes: BasicFileAttributes): FileVisitResult {
    if (!fileFilter.test(sourceFile)) {
      return FileVisitResult.CONTINUE
    }

    val targetFile = sourceToTargetFile(sourceFile)
    Files.copy(sourceFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES)
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

      try {
        Thread.sleep(10)
      }
      catch (ignored: InterruptedException) {
        throw e
      }
    }
  }
}

@JvmOverloads
fun substituteTemplatePlaceholders(inputFile: Path, outputFile: Path, placeholderChar: String, values: List<Pair<String, String>>, mustUseAllPlaceholders: Boolean = true) {
  var result = Files.readString(inputFile)

  val missingPlaceholders = mutableListOf<String>()
  for ((name, value) in values) {
    if (name.contains(placeholderChar)) {
      error("Do not use placeholder '$placeholderChar' in name: $name")
    }

    val placeholder = "$placeholderChar$name$placeholderChar"
    if (!result.contains(placeholder)) {
      missingPlaceholders.add(placeholder)
    }

    result = result.replace(placeholder, value)
  }

  if (mustUseAllPlaceholders && missingPlaceholders.isNotEmpty()) {
    error("Missing placeholders [${missingPlaceholders.joinToString(" ")}] in template file $inputFile")
  }

  val unsubstituted = result
    .split('\n')
    .mapIndexed { line, s -> "line ${line + 1}: $s" }
    .filter { Regex(Regex.escape(placeholderChar) + ".+" + Regex.escape(placeholderChar)).containsMatchIn(it) }
    .joinToString("\n")
  if (unsubstituted.isNotBlank()) {
    error("Some template parameters were left unsubstituted in template file $inputFile:\n$unsubstituted")
  }

  Files.createDirectories(outputFile.parent)
  Files.writeString(outputFile, result)
}

inline fun transformFile(file: Path, task: (tempFile: Path) -> Unit) {
  val tempFile = file.parent.resolve("${file.fileName}.tmp")
  try {
    task(tempFile)
    Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING)
  }
  finally {
    Files.deleteIfExists(tempFile)
  }
}