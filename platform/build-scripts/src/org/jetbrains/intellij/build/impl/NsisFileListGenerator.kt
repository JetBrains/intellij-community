// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

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
        @Suppress("SpellCheckingInspection")
        out.write("SetOutPath \"\$INSTDIR${if (relativePath.isEmpty()) "" else "\\"}${escapeWinPath(relativePath)}\"\n")

        for (file in files) {
          out.write("File \"${file.toAbsolutePath().normalize()}\"\n")
        }
      }
    }
  }

  fun generateUninstallerFile(outputFile: Path, @Suppress("SpellCheckingInspection") installDir: String = "\$INSTDIR") {
    Files.newBufferedWriter(outputFile).use { out ->
      filesRelativePaths.sorted().forEach {
        out.write("Delete \"${installDir}\\${escapeWinPath(it)}\"\n")
        if (it.endsWith(".py")) {
          out.write("Delete \"${installDir}\\${escapeWinPath(it)}c\"\n") //.pyc
        }
      }

      out.newLine()

      for (it in directoryToFiles.keys.sorted().asReversed()) {
        if (!it.isEmpty()) {
          out.write("RmDir /r \"${installDir}\\${escapeWinPath(it)}\\__pycache__\"\n")
          out.write("RmDir \"${installDir}\\${escapeWinPath(it)}\"\n")
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
}

private fun escapeWinPath(dir: String): String {
  return dir.replace('/', '\\').replace("\\$", "\\$\\$")
}