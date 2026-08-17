// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.intellij.openapi.util.text.Formats
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.ApiStatus.Internal
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
  doCopyFile(file = file, target = targetDir.resolve(file.fileName), targetDir = targetDir, overwrite = false)
}

fun copyFileToDir(file: Path, targetDir: Path, overwrite: Boolean) {
  doCopyFile(file = file, target = targetDir.resolve(file.fileName), targetDir = targetDir, overwrite = overwrite)
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
  doCopyFile(file = file, target = target, targetDir = target.parent, overwrite = false)
}

fun copyFile(file: Path, target: Path, overwrite: Boolean) {
  doCopyFile(file = file, target = target, targetDir = target.parent, overwrite = overwrite)
}

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
private fun doCopyFile(file: Path, target: Path, targetDir: Path, overwrite: Boolean) {
  Files.createDirectories(targetDir)
  if (overwrite) {
    Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
  }
  else {
    Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES)
  }
}

/**
 * Copies [sourceDir] into [targetDir], and returns the files it wrote.
 *
 * The return value is for a caller that owns the target directory's contents and therefore has to know what
 * landed in it - a dev-mode assembly deletes whatever it did not put in `bin` itself. Most callers ignore it.
 */
fun copyDir(
  sourceDir: Path,
  targetDir: Path,
  dirFilter: Predicate<Path>? = null,
  fileFilter: Predicate<Path>? = null,
): List<Path> {
  return copyDir(sourceDir, targetDir, overwrite = false, dirFilter = dirFilter, fileFilter = fileFilter)
}

/**
 * [copyDir] with an explicit collision policy. When [overwrite] is `true`, files from an earlier layout are
 * replaced and still reported in the returned list; directories are merged in both modes.
 *
 * A symbolic link is reproduced as a link, never dereferenced: a JCEF or JBR tree is a tree of macOS frameworks, where
 * following one would break the framework layout and multiply its size. Regular files are copied through
 * [doCopyFile]'s option set, so the same copy-on-write path applies here, per file.
 */
fun copyDir(
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
