// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic

import java.util.regex.Pattern
/**
 * @author nik
 */
@CompileStatic
class NsisFileListGenerator {
  private final Map<String, List<File>> directoryToFiles = [:]
  private final List<String> filesRelativePaths = []

  void addDirectory(String directoryPath, List<String> relativeFileExcludePatterns = []) {
    def excludePatterns = relativeFileExcludePatterns.collect { Pattern.compile(FileUtil.convertAntToRegexp(it)) }
    processDirectory(new File(directoryPath), "", excludePatterns)
  }

  void generateInstallerFile(File outputFile) {
    outputFile.withWriter { BufferedWriter out ->
      directoryToFiles.each {
        if (!it.value.empty) {
          out.newLine()
          out.writeLine("SetOutPath \"\$INSTDIR${it.key.isEmpty() ? "" : "\\"}${toWinPath(it.key)}\"")

          it.value.each {
            out.writeLine("File \"${it.absolutePath}\"")
          }
        }
      }
    }
  }

  void generateUninstallerFile(String installDir = "\$INSTDIR", File outputFile) {
    outputFile.withWriter { BufferedWriter out ->
      filesRelativePaths.toSorted().each {
        out.writeLine("Delete \"${installDir}\\${toWinPath(it)}\"")
        if (it.endsWith(".py")) {
          out.writeLine("Delete \"${installDir}\\${toWinPath(it)}c\"") //.pyc
        }
      }

      out.newLine()

      directoryToFiles.keySet().toSorted().reverseEach {
        if (!it.empty) {
          out.writeLine("RmDir /r \"${installDir}\\${toWinPath(it)}\\__pycache__\"")
          out.writeLine("RmDir \"${installDir}\\${toWinPath(it)}\"")
        }
      }
      out.writeLine("RmDir \"${installDir}\"")
    }
  }

  private static String toWinPath(String dir) {
    return dir.replace('/', '\\')
  }


  private void processDirectory(File directory, String relativePath, List<Pattern> excludePatterns) {
    def files = directory.listFiles()
    if (files == null) {
      throw new IOException("Not a directory: $directory")
    }
    files.sort { it.name }
    for (child in files) {
      String childPath = "${(relativePath.isEmpty() ? "" : "$relativePath/")}$child.name"
      if (excludePatterns.any { it.matcher(childPath).matches() }) {
        continue
      }
      if (child.isFile()) {
        filesRelativePaths << childPath
        directoryToFiles.get(relativePath, []) << child
      }
      else {
        processDirectory(child, childPath, excludePatterns)
        if (directoryToFiles.containsKey(childPath)) {
          //register all parent directories for directories with files to ensure that they will be deleted by uninstaller
          directoryToFiles.putIfAbsent(relativePath, [])
        }
      }
    }
  }
}