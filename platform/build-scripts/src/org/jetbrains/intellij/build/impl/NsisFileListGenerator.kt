// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path

private const val MAX_PATH = 260

internal class NsisFileListGenerator {
  private val directoryToFiles = LinkedHashMap<String, MutableList<Path>>()
  private val filesRelativePaths = mutableListOf<String>()

  fun addDirectory(directory: Path) {
    processDirectory(directory, "")
  }

  fun generateInstallerFile(outputFile: Path) {
    Files.newBufferedWriter(outputFile).use { out ->
      for ((relativePath, files) in directoryToFiles) {
        if (files.isEmpty()) continue

        out.newLine()

        scriptWithLongPathSupport(out, relativePath, files.map { it.fileName.toString() }) {
          out.write($$"SetOutPath \"$INSTDIR" + (if (relativePath.isEmpty()) "" else "\\") + escapeWinPath(relativePath) + "\"\n")
          for (file in files) {
            out.write("File \"" + file.toAbsolutePath().normalize() + "\"\n")
          }
        }
      }
    }
  }

  fun generateUninstallerFile(outputFile: Path) {
    Files.newBufferedWriter(outputFile).use { out ->
      filesRelativePaths.sorted().forEach {
        scriptWithLongPathSupport(out, relativePath = null, listOf(it)) {
          out.write($$"Delete \"$INSTDIR\\" + escapeWinPath(it) + "\"\n")
          if (it.endsWith(".py")) {
            out.write($$"Delete \"$INSTDIR\\" + escapeWinPath(it) + "c\"\n") //.pyc
          }
        }
      }

      out.newLine()

      for (it in directoryToFiles.keys.sorted().asReversed()) {
        if (!it.isEmpty()) {
          scriptWithLongPathSupport(out, relativePath = null, listOf(it)) {
            out.write($$"RMDir /R \"$INSTDIR\\" + escapeWinPath(it) + "\\__pycache__\"\n")
            out.write($$"RMDir \"$INSTDIR\\" + escapeWinPath(it) + "\"\n")
          }
        }
      }

      out.write($$"RMDir \"$INSTDIR\"\n")
    }
  }

  private fun processDirectory(directory: Path, relativePath: String) {
    val files = Files.newDirectoryStream(directory).use { stream -> stream.sortedBy { it.fileName.toString() } }
    for (child in files) {
      val childPath = (if (relativePath.isEmpty()) "" else "${relativePath}/") + child.fileName.toString()
      if (Files.isRegularFile(child)) {
        filesRelativePaths.add(childPath)
        directoryToFiles.computeIfAbsent(relativePath) { mutableListOf() }.add(child)
      }
      else {
        processDirectory(child, childPath)
        if (directoryToFiles.containsKey(childPath)) {
          //register all parent directories for directories with files to ensure that they will be deleted by uninstaller
          directoryToFiles.putIfAbsent(relativePath, mutableListOf())
        }
      }
    }
  }

  private fun escapeWinPath(dir: String): String = dir.replace('/', '\\').replace("$", "$$")

  private fun scriptWithLongPathSupport(writer: BufferedWriter, relativePath: String?, files: List<String>, actionWithOutPath: () -> Unit) {
    val areLongPathsPresent = guessMaxPathLength(relativePath, files) > MAX_PATH
    if (areLongPathsPresent) {
      // For long paths in the installer, we perform a special maneuver.
      // Prepend the INSTDIR with "\\?\" so that WinAPI functions won't check its length and will allow working with the file.
      writer.write($$"""
        Push $INSTDIR
        GetFullPathName $INSTDIR "$INSTDIR"
        StrCpy $INSTDIR "\\?\$INSTDIR"
        """.trimIndent() + "\n")
    }

    actionWithOutPath()

    if (areLongPathsPresent) {
      // Clean up.
      writer.write($$"Pop $INSTDIR\n")
    }
  }

  private fun guessMaxPathLength(relativePath: String?, files: List<String>): Int {
    // Guess the typical length of $INSTDIR plus a small margin to be safe.
    // NOTE: The AppData path for non-admin installation might be longer than the one in Program Files, so let's consider that here.
    // Also, "IntelliJ IDEA Community Edition" is the longest product name so far.
    val instDirGuessedLength = "C:\\Users\\some-reasonably-long-user-name\\AppData\\Local\\JetBrains\\IntelliJ IDEA Community Edition 2024.1.2.SNAPSHOT\\".length + 10
    val directoryPathLength = instDirGuessedLength + (relativePath?.length?.let { it + 1 /* backslash */ } ?: 0)
    return directoryPathLength + (files.maxOfOrNull { it.length } ?: 0)
  }
}
