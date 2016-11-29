/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  void generateUninstallerFile(File outputFile) {
    outputFile.withWriter { BufferedWriter out ->
      filesRelativePaths.toSorted().each {
        out.writeLine("Delete \"\$INSTDIR\\${toWinPath(it)}\"")
        if (it.endsWith(".py")) {
          out.writeLine("Delete \"\$INSTDIR\\${toWinPath(it)}c\"") //.pyc
        }
      }

      out.newLine()

      directoryToFiles.keySet().toSorted().reverseEach {
        if (!it.empty) {
          out.writeLine("RmDir /r \"\$INSTDIR\\${toWinPath(it)}\\__pycache__\"");
          out.writeLine("RmDir \"\$INSTDIR\\${toWinPath(it)}\"");
        }
      }
      out.writeLine("RmDir \"\$INSTDIR\"")
    }
  }

  private static String toWinPath(String dir) {
    return dir.replace('/', '\\');
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