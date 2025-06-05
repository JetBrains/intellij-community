// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

private const val MAX_PATH = 260

internal class NsisFileListGenerator {
  private val directoryToFiles = LinkedHashMap<String, MutableList<Path>>()
  private val filesRelativePaths = mutableListOf<String>()

  fun addDirectory(directoryPath: String, relativeFileExcludePatterns: List<String> = emptyList()) {
    val excludePatterns = relativeFileExcludePatterns.map { Pattern.compile(FileUtil.convertAntToRegexp(it)) }
    processDirectory(Path.of(directoryPath), "", excludePatterns)
  }

  fun generateInstallerFile(outputFile: Path) {
    Files.newBufferedWriter(outputFile).use { out ->
      for ((relativePath, files) in directoryToFiles) {
        if (files.isEmpty()) {
          continue
        }

        out.write("\n")

        val installDir = $$"$INSTDIR"
        scriptWithLongPathSupport(out, installDir, relativePath, files.map { it.fileName.toString() }) {
          out.write("SetOutPath \"$installDir${if (relativePath.isEmpty()) "" else "\\"}${escapeWinPath(relativePath)}\"\n")

          for (file in files) {
            out.write("File \"${file.toAbsolutePath().normalize()}\"\n")
          }
        }
      }
    }
  }

  fun generateUninstallerFile(outputFile: Path, installDir: String = $$"$INSTDIR") {
    Files.newBufferedWriter(outputFile).use { out ->
      filesRelativePaths.sorted().forEach {
        scriptWithLongPathSupport(out, installDir, null, listOf(it)) {
          out.write("Delete \"${installDir}\\${escapeWinPath(it)}\"\n")
          if (it.endsWith(".py")) {
            out.write("Delete \"${installDir}\\${escapeWinPath(it)}c\"\n") //.pyc
          }
        }
      }

      out.newLine()

      for (it in directoryToFiles.keys.sorted().asReversed()) {
        if (!it.isEmpty()) {
          scriptWithLongPathSupport(out, installDir, null, listOf(it)) {
            out.write("RmDir /r \"${installDir}\\${escapeWinPath(it)}\\__pycache__\"\n")
            out.write("RmDir \"${installDir}\\${escapeWinPath(it)}\"\n")
          }
        }
      }
      out.write("RmDir \"${installDir}\"\n")
    }
  }

  private fun processDirectory(directory: Path, relativePath: String, excludePatterns: List<Pattern>) {
    val files = Files.newDirectoryStream(directory).use { stream -> stream.sortedBy { it.fileName.toString() } }
    for (child in files) {
      val childPath = (if (relativePath.isEmpty()) "" else "$relativePath/") + child.fileName.toString()
      if (excludePatterns.any { it.matcher(childPath).matches() }) {
        continue
      }

      if (Files.isRegularFile(child)) {
        filesRelativePaths.add(childPath)
        directoryToFiles.computeIfAbsent(relativePath) { mutableListOf() }.add(child)
      }
      else {
        processDirectory(child, childPath, excludePatterns)
        if (directoryToFiles.containsKey(childPath)) {
          //register all parent directories for directories with files to ensure that they will be deleted by uninstaller
          directoryToFiles.putIfAbsent(relativePath, mutableListOf())
        }
      }
    }
  }

  private fun escapeWinPath(dir: String): String = dir.replace('/', '\\').replace("\\$", "\\$\\$")

  private fun scriptWithLongPathSupport(
    writer: BufferedWriter,
    instDirVariable: String,
    relativePath: String?,
    files: List<String>,
    actionWithOutPath: () -> Unit
  ) {
    assert(instDirVariable.startsWith("$"))

    val areLongPathsPresent = guessMaxPathLength(relativePath, files) > MAX_PATH
    if (areLongPathsPresent) {
      // For long paths in the installer, we perform a special maneuver.
      // Prepend the INSTDIR with "\\?\" so that WinAPI functions won't check its length and will allow working with the file.
      writer.write(
        """
        Push $instDirVariable
        GetFullPathName $instDirVariable $instDirVariable
        StrCpy $instDirVariable "\\?\$instDirVariable"
      """.trimIndent() + "\n"
      )
    }

    actionWithOutPath()

    if (areLongPathsPresent) {
      // Clean up:
      writer.write("Pop $instDirVariable\n")
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
