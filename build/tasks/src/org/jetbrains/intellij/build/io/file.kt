// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package org.jetbrains.intellij.build.io

import com.intellij.openapi.util.text.Formats
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.function.Predicate
import java.util.regex.Pattern

val W_CREATE_NEW: EnumSet<StandardOpenOption> = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)

fun copyFileToDir(file: Path, targetDir: Path) {
  doCopyFile(file, targetDir.resolve(file.fileName), targetDir)
}

fun moveFile(source: Path, target: Path) {
  Files.createDirectories(target.parent)
  Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
}

fun moveFileToDir(file: Path, targetDir: Path): Path {
  Files.createDirectories(targetDir)
  return Files.move(file, targetDir.resolve(file.fileName))
}

fun copyFile(file: Path, target: Path) {
  doCopyFile(file = file, target = target, targetDir = target.parent)
}

private fun doCopyFile(file: Path, target: Path, targetDir: Path) {
  Files.createDirectories(targetDir)
  Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
}

@JvmOverloads
fun copyDir(sourceDir: Path, targetDir: Path, dirFilter: Predicate<Path>? = null, fileFilter: Predicate<Path>? = null) {
  Files.createDirectories(targetDir)
  Files.walkFileTree(sourceDir, CopyDirectoryVisitor(
    sourceDir,
    targetDir,
    dirFilter = dirFilter ?: Predicate { true },
    fileFilter = fileFilter ?: Predicate { true },
  ))
}

inline fun writeNewFile(file: Path, task: (FileChannel) -> Unit) {
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

fun deleteDir(startDir: Path) {
  if (!Files.exists(startDir)) {
    return
  }

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
fun substituteTemplatePlaceholders(inputFile: Path,
                                   outputFile: Path,
                                   placeholder: String,
                                   values: List<Pair<String, String>>,
                                   mustUseAllPlaceholders: Boolean = true,
                                   convertToUnixLineEndings: Boolean = false) {
  var result = Files.readString(inputFile)

  if (convertToUnixLineEndings) {
    result = result.replace("\r", "")
  }

  val missingPlaceholders = mutableListOf<String>()
  for ((name, value) in values) {
    check (!name.contains(placeholder)) {
      "Do not use placeholder '$placeholder' in name: $name"
    }

    val s = "$placeholder$name$placeholder"
    if (!result.contains(s)) {
      missingPlaceholders.add(s)
    }

    result = result.replace(s, value)
  }

  check(!mustUseAllPlaceholders || missingPlaceholders.isEmpty()) {
    "Missing placeholders [${missingPlaceholders.joinToString(" ")}] in template file $inputFile"
  }

  val escapedPlaceHolder = Pattern.quote(placeholder)
  val regex = Regex("$escapedPlaceHolder.+$escapedPlaceHolder")
  val unsubstituted = result
    .splitToSequence('\n')
    .mapIndexed { line, s -> "line ${line + 1}: $s" }
    .filter(regex::containsMatchIn)
    .joinToString("\n")
  check (unsubstituted.isBlank()) {
    "Some template parameters were left unsubstituted in template file $inputFile:\n$unsubstituted"
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

@Internal
fun logFreeDiskSpace(dir: Path, phase: String) {
  Span.current().addEvent("free disk space", Attributes.of(
    AttributeKey.stringKey("phase"), phase,
    AttributeKey.stringKey("usableSpace"), Formats.formatFileSize(Files.getFileStore(dir).usableSpace),
    AttributeKey.stringKey("dir"), dir.toString(),
  ))
}