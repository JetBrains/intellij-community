// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.intellij.openapi.util.text.Formats
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
  return doCopyDirectory(sourceDir, targetDir, overwrite, dirFilter, fileFilter)
}

inline fun writeNewFile(file: Path, task: (FileChannel) -> Unit) {
  Files.createDirectories(file.parent)
  FileChannel.open(file, W_CREATE_NEW).use {
    task(it)
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
