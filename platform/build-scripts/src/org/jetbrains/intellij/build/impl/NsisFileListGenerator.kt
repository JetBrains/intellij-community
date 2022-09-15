// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

import java.util.regex.Pattern

internal class NsisFileListGenerator {
  private val directoryToFiles = HashMap<String, MutableList<File>>()
  private val filesRelativePaths = mutableListOf<String>()

  fun addDirectory(directoryPath: String, relativeFileExcludePatterns: List<String> = emptyList()) {
    val excludePatterns = relativeFileExcludePatterns.map { Pattern.compile(FileUtil.convertAntToRegexp(it)) }
    processDirectory(File(directoryPath), "", excludePatterns)
  }

  fun generateInstallerFile(outputFile: Path) {
    Files.newBufferedWriter(outputFile).use { out ->
      for (it in directoryToFiles) {
        if (!it.value.isEmpty()) {
          out.newLine()
          @Suppress("SpellCheckingInspection")
          out.write("SetOutPath \"\$INSTDIR${if (it.key.isEmpty()) "" else "\\"}${escapeWinPath(it.key)}\"\n")

          it.value.forEach {
            out.write("File \"${it.absolutePath}\"\n")
          }
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

  private fun processDirectory(directory: File, relativePath: String, excludePatterns: List<Pattern>) {
    val files = directory.listFiles() ?: throw IOException("Not a directory: $directory")
    files.sortBy { it.name }
    for (child in files) {
      val childPath = "${(if (relativePath.isEmpty()) "" else "$relativePath/")}$child.name"
      if (excludePatterns.any { it.matcher(childPath).matches() }) {
        continue
      }

      if (child.isFile) {
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