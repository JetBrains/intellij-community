// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.LineColumn
import java.io.File

/**
 * @author Bo Zhang
 */
class ProjectAbsoluteFileLocationFilter(private val myProject: Project) : Filter {
  companion object {
    val FILE_ENDING_EXTENSION_PATTERN = "((\\.[\\w-]+)+)[^\\w-.]*".toPattern()
  }

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    return myProject.basePath?.takeIf {
      line.contains(it)
    }?.let { findProjectAbsoluteFileLocation(line, it, entireLength) }
  }

  private fun findProjectAbsoluteFileLocation(line: String,
                                              projectBaseDir: String,
                                              entireLength: Int): Filter.Result? {
    val projectBasePathStartIndex = line.indexOf(projectBaseDir)
    val projectBasePathEndIndex = projectBasePathStartIndex + projectBaseDir.length
    val substringContainingTargetFile = line.substring(0, indexOfNextSeparatorOrEnd(line, projectBasePathEndIndex))
    val lastSeparatorCharIndex = substringContainingTargetFile.indexOfLast { it == File.separatorChar }

    if (lastSeparatorCharIndex >= projectBasePathEndIndex) {
      val substringAfterLastSeparator = substringContainingTargetFile.substring(lastSeparatorCharIndex + 1)
      val matcher = FILE_ENDING_EXTENSION_PATTERN.matcher(substringAfterLastSeparator)
      if (matcher.find()) {
        // find the boundary after the file extension
        val extensionEndIndex = lastSeparatorCharIndex + matcher.end(2)
        val absoluteProjectFilePath = line.substring(projectBasePathStartIndex, extensionEndIndex + 1)
        val lineColumn = inferLineColumnNumber(line, extensionEndIndex)
        val hyperlinkInfo = LazyAbsoluteFileHyperLinkInfo(myProject, absoluteProjectFilePath, absoluteProjectFilePath, lineColumn.line,
                                                          lineColumn.column)
        val textStartOffset = entireLength - line.length
        return Filter.Result(textStartOffset + projectBasePathStartIndex,
                             textStartOffset + extensionEndIndex + 1,
                             hyperlinkInfo)
      }
    }
    return null
  }

  private fun inferLineColumnNumber(line: String, extensionEndIndex: Int): LineColumn {
    val substringAfterFileExtension = line.substring(extensionEndIndex + 1).substringBefore(' ')
    return LineColumnNumberParser.COMPOSITE.parse(substringAfterFileExtension) ?: LineColumn.of(0, 0)
  }

  private fun indexOfNextSeparatorOrEnd(line: String, projectBasePathEndIndex: Int): Int {
    for (i in projectBasePathEndIndex until line.length) {
      if (line[i].isWhitespace() || line[i] == File.pathSeparatorChar) {
        return i
      }
    }
    return line.length
  }
}

