// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.intellij.openapi.util.text.Formats
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.Predicate
import java.util.regex.Pattern

fun copyFileToDir(file: Path, targetDir: Path) {
  doCopyFile(file = file, target = targetDir.resolve(file.fileName), targetDir = targetDir)
}

fun moveFile(source: Path, target: Path) {
  Files.createDirectories(target.parent)
  Files.move(source, target)
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
  Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES)
}

fun copyDir(sourceDir: Path, targetDir: Path, dirFilter: Predicate<Path>? = null, fileFilter: Predicate<Path>? = null) {
  Files.createDirectories(targetDir)
  val dirFilter = dirFilter ?: Predicate { true }
  val fileFilter = fileFilter ?: Predicate { true }
  Files.walkFileTree(sourceDir, CopyDirectoryVisitor(sourceDir, targetDir, dirFilter, fileFilter))
}

/**
 * Hardlinks [file] into [target] instead of copying it, falling back to a copy whenever a link is
 * impossible - a different filesystem, a read-only share, a filesystem without hardlinks.
 *
 * Only for a [file] that is an entry of an immutable cache, and only for a [target] that nothing will
 * rewrite in place: a link makes the two the same bytes on disk, so patching the target afterwards
 * would corrupt the cache for every later build. Distributions are therefore always copied - only an
 * in-process dev-mode assembly turns this on, through `BuildOptions.linkImmutableCacheEntries`.
 */
fun linkOrCopyFile(file: Path, target: Path) {
  Files.createDirectories(target.parent)
  try {
    Files.deleteIfExists(target)
    Files.createLink(target, file)
  }
  catch (_: IOException) {
    Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
  }
  catch (_: UnsupportedOperationException) {
    Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
  }
}

/**
 * [linkOrCopyFile] for a whole tree. Same constraints, and the same reason for them.
 */
fun linkOrCopyDir(sourceDir: Path, targetDir: Path) {
  Files.createDirectories(targetDir)
  Files.walkFileTree(sourceDir, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
      Files.createDirectories(targetDir.resolve(sourceDir.relativize(directory).toString()))
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(sourceFile: Path, attributes: BasicFileAttributes): FileVisitResult {
      linkOrCopyFile(sourceFile, targetDir.resolve(sourceDir.relativize(sourceFile).toString()))
      return FileVisitResult.CONTINUE
    }
  })
}

inline fun writeNewFile(file: Path, task: (FileChannel) -> Unit) {
  Files.createDirectories(file.parent)
  FileChannel.open(file, W_CREATE_NEW).use {
    task(it)
  }
}

private class CopyDirectoryVisitor(
  private val sourceDir: Path,
  private val targetDir: Path,
  private val dirFilter: Predicate<Path>,
  private val fileFilter: Predicate<Path>
) : SimpleFileVisitor<Path>() {
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
    catch (_: FileAlreadyExistsException) { }
    return FileVisitResult.CONTINUE
  }

  override fun visitFile(sourceFile: Path, attributes: BasicFileAttributes): FileVisitResult {
    if (!fileFilter.test(sourceFile)) {
      return FileVisitResult.CONTINUE
    }

    val targetFile = sourceToTargetFile(sourceFile)
    Files.copy(sourceFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
    return FileVisitResult.CONTINUE
  }
}

@JvmOverloads
fun substituteTemplatePlaceholders(
  inputFile: Path,
  outputFile: Path,
  placeholder: String,
  values: List<Pair<String, String>>,
  mustUseAllPlaceholders: Boolean = true,
  convertToUnixLineEndings: Boolean = false
) {
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
