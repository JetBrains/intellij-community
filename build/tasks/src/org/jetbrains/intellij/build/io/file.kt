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
  doLinkOrCopyFile(file = file, target = target, isSymbolicLink = Files.isSymbolicLink(file))
}

/**
 * [linkOrCopyFile] for a caller that has already created `target.parent` and already knows whether
 * [file] is a symbolic link, so that neither costs an extra syscall per file of a tree.
 */
private fun doLinkOrCopyFile(file: Path, target: Path, isSymbolicLink: Boolean) {
  // a symlink is recreated, never hardlinked: `link` follows symlinks on macOS but not on Linux, so a hardlinked
  // one would mean a different thing per OS - and what a tree of frameworks needs is the link itself
  if (!isSymbolicLink) {
    try {
      Files.deleteIfExists(target)
      Files.createLink(target, file)
      return
    }
    catch (_: IOException) {
    }
    catch (_: UnsupportedOperationException) {
    }
  }
  Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS)
}

/**
 * [linkOrCopyFile] for a whole tree. Same constraints, and the same reason for them.
 *
 * Symbolic links are reproduced as links, as [copyDir] does - a JCEF or JBR tree is a tree of macOS
 * frameworks, where dereferencing one would both break the framework layout and multiply its size.
 */
fun linkOrCopyDir(sourceDir: Path, targetDir: Path) {
  Files.createDirectories(targetDir)
  Files.walkFileTree(sourceDir, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
      Files.createDirectories(targetDir.resolve(sourceDir.relativize(directory).toString()))
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(sourceFile: Path, attributes: BasicFileAttributes): FileVisitResult {
      // `walkFileTree` does not follow links, so a link to a directory arrives here too, as a file
      doLinkOrCopyFile(
        file = sourceFile,
        target = targetDir.resolve(sourceDir.relativize(sourceFile).toString()),
        isSymbolicLink = attributes.isSymbolicLink,
      )
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
