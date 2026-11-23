// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.plugins

import com.intellij.util.io.PosixFilePermissionsUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readLines

/** The name of the file in the `<target name>_dist` directory of an `ij_plugin` target that states the modes of the plugin files. */
@ApiStatus.Internal
const val IJ_PLUGIN_FILE_ATTRIBUTES_FILE_NAME: String = "file-attributes.txt"

/** The mode of a regular file in a plugin distribution that `file-attributes.txt` does not name. */
@ApiStatus.Internal
const val IJ_PLUGIN_DEFAULT_FILE_MODE: Int = 0b110_100_100 // 0644

private const val MAX_FILE_MODE = 0b111_111_111 // 0777

private val FILE_MODE_LINE_REGEX = Regex("(.+):([^:]*)")

/**
 * Reads the `file-attributes.txt` file that the `ij_plugin` Bazel rule writes for one plugin.
 *
 * Each line of the file is `<relative path>:<mode>`. The path is relative to the plugin directory and uses `/`. The mode is octal.
 * The returned map has the path as the key and the mode as the value. It names only the files whose mode is not
 * [IJ_PLUGIN_DEFAULT_FILE_MODE].
 */
@ApiStatus.Internal
fun readIjPluginFileModes(file: Path): Map<String, Int> {
  val result = LinkedHashMap<String, Int>()
  for (line in file.readLines()) {
    if (line.isEmpty()) {
      continue
    }
    val match = FILE_MODE_LINE_REGEX.matchEntire(line)
    require(match != null) { "$file: expected `<relative path>:<mode>`, but got '$line'" }
    val (path, modeText) = match.destructured
    val mode = modeText.toIntOrNull(8)?.takeIf { it in 0..MAX_FILE_MODE }
    require(mode != null) { "$file: expected an octal mode from 0000 to 0777 for '$path', but got '$modeText'" }
    val oldMode = result.put(path, mode)
    require(oldMode == null) { "$file: '$path' is stated twice" }
  }
  return result
}

/**
 * Sets the POSIX permissions of each regular file in [pluginDirectory]: a file from [fileModes] gets its mode, and every other file gets
 * [IJ_PLUGIN_DEFAULT_FILE_MODE]. A symbolic link is not followed.
 *
 * Bazel writes each file of the `ij_plugin` output with the mode `0555`, so a copy of the output must get its modes from
 * `file-attributes.txt`. The archive and distribution builders then read the executable bit from the disk.
 * The function does nothing on a file system without POSIX permissions.
 */
@ApiStatus.Internal
fun applyIjPluginFileModes(pluginDirectory: Path, fileModes: Map<String, Int>) {
  val remainingPaths = HashSet(fileModes.keys)
  Files.walkFileTree(pluginDirectory, object : SimpleFileVisitor<Path>() {
    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (attrs.isRegularFile) {
        val relativePath = pluginDirectory.relativize(file).invariantSeparatorsPathString
        remainingPaths.remove(relativePath)
        val mode = fileModes[relativePath] ?: IJ_PLUGIN_DEFAULT_FILE_MODE
        if (isPosix) {
          Files.setPosixFilePermissions(file, PosixFilePermissionsUtil.fromUnixMode(mode))
        }
        else {
          Files.getFileAttributeView(file, DosFileAttributeView::class.java)?.setReadOnly(false)
        }
      }
      return FileVisitResult.CONTINUE
    }
  })
  check(remainingPaths.isEmpty()) {
    "$IJ_PLUGIN_FILE_ATTRIBUTES_FILE_NAME states modes for files that are not regular files in $pluginDirectory: ${remainingPaths.sorted()}"
  }
}

private val isPosix by lazy { "posix" in FileSystems.getDefault().supportedFileAttributeViews() }
