package com.intellij.tools.build.bazel.ijPluginPackager

import java.nio.file.Path
import kotlin.io.path.writeText

/** The mode of a regular file in the plugin distribution that states no other mode. `file-attributes.txt` omits it. */
internal const val DEFAULT_FILE_MODE: Int = 0b110_100_100 // 0644

/** The largest mode that the packager accepts. The setuid, setgid and sticky bits are not supported. */
internal const val MAX_FILE_MODE: Int = 0b111_111_111 // 0777

/**
 * Writes the `file-attributes.txt` file of one plugin distribution.
 *
 * The file has one `<relative path>:<mode>` line for each regular file whose mode is not [DEFAULT_FILE_MODE].
 * The path is relative to the plugin directory and uses `/`. The mode has four octal digits. The lines are sorted by path.
 *
 * Bazel writes each file of the plugin directory with the mode `0555`, so the consumers read the modes from this file.
 * `readIjPluginFileModes` in the build scripts reads it.
 */
internal fun writeFileAttributes(outputFile: Path, fileModes: Map<String, Int>) {
  val lines = fileModes.entries
    .filter { it.value != DEFAULT_FILE_MODE }
    .sortedBy { it.key }
    .map { "${it.key}:${it.value.toString(8).padStart(4, '0')}" }
  outputFile.writeText(lines.joinToString(separator = "") { "$it\n" })
}
