/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.filters

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

enum class ParsingState {
  NORMAL, PATH, CANCELED_PATH
}

/**
 *  Filter that highlights absolute path as hyperlinks in console output. This filter is effective in all console output, including build
 *  output, sync output, run test output, built-in terminal emulator, etc. Therefore, we manually parse the string instead of using regex
 *  for maximum performance.
 *
 *  @param isEnabled the lazy value indicating whether this filter is enabled.
 */
class GenericFileFilter(private val project: Project,
                        private val localFileSystem: LocalFileSystem,
                        private val isEnabled: () -> Boolean) : Filter {
  companion object {
    /**
     *  Max filename considered during parsing. Do not confuse with file path, which may contain several file names separated by '/' or '\'.
     *
     * Modern popular FS do not allow file names longer than 255.*/
    const val FILENAME_MAX = 255

    /**
     * Min path length to be considered.
     *
     * E.g. lonely slashes should be ignored.
     */
    const val PATH_MIN = 2
  }

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    if (!isEnabled()) {
      return null
    }

    val indexOffset = entireLength - line.length
    val items = mutableListOf<Filter.ResultItem>()
    var state = ParsingState.NORMAL
    var pathStartIndex = -1
    var lastPathSegmentStart = -1
    var candidateItem: Filter.ResultItem? = null
    var i = 0

    fun addPreviousCandidate() {
      if (candidateItem != null) {
        items += candidateItem!!
      }
      candidateItem = null
    }

    fun startPathMode(){
      state = ParsingState.PATH
      pathStartIndex = i
      lastPathSegmentStart = i
      addPreviousCandidate()
    }

    fun startNormalMode() {
      state = ParsingState.NORMAL
      addPreviousCandidate()
    }

    fun startCanceledMode() {
      state = ParsingState.CANCELED_PATH
      addPreviousCandidate()
    }

    fun findValidResult(pathEndIndex: Int, lineNumber: Int, columnNumber: Int): Filter.ResultItem? {
      if (pathEndIndex - lastPathSegmentStart > FILENAME_MAX) return null
      if (pathEndIndex - pathStartIndex < PATH_MIN) return null

      val path = line.substring(pathStartIndex, pathEndIndex)
      if (path == "/") {
        // Ignore single slashes, as these are probably referring to something
        // other than the file system root (e.g. progress indicators like "[10 / 1,000]").
        return null
      }
      val file = try {
        localFileSystem.findFileByPathIfCached(path)
      } catch (t: Throwable) {
        // We interpret any exception to mean the file is not found.
        null
      }
      return if (file != null) {
        Filter.ResultItem(
          indexOffset + pathStartIndex,
          indexOffset + i,
          OpenFileHyperlinkInfo(project, file, lineNumber, columnNumber))
      }
      else {
        null
      }
    }

    fun findValidResultWithNumbers(pathEndIndex: Int): Filter.ResultItem? {
      val lineNumber: Int
      var columnNumber = 1
      // Try to parse as "path: (line, column):"
      if (line.startsWith(" (", i + 1)) {
        i += 3
        lineNumber = line.takeWhileFromIndex(i) { it.isDigit() }?.also { i += it.length }.safeToIntOrDefault(1)
        if (line.startsWith(", ", i)) {
          i += 2
          columnNumber = line.takeWhileFromIndex(i) { it.isDigit() }?.also { i += it.length }.safeToIntOrDefault(1)
          if (line.startsWith("):", i)) {
            i += 2
          }
        }
      }
      else {
        // Try to parse as path:line:column:
        lineNumber = line.takeWhileFromIndex(i + 1) { it.isDigit() }?.also { i += it.length }.safeToIntOrDefault(1)
        columnNumber =
          if (line.getOrNull(++i) == ':')
            line.takeWhileFromIndex(++i) { it.isDigit() }?.also { i += it.length }.safeToIntOrDefault(1)
          else
            1
      }
      return findValidResult(pathEndIndex, lineNumber - 1, columnNumber - 1)
    }

    while (i < line.length) {
      when (state) {
        ParsingState.NORMAL -> {
          when {
            line[i] == '/' -> {
              // Start parsing a Linux path
              startPathMode()
            }
            line[i] in 'A'..'Z' && (line.startsWith(":\\", startIndex = i + 1) || line.startsWith(":/", startIndex = i + 1) ) -> {
              // Start parsing a Windows path
              startPathMode()
              i += 2
            }
          }
        }
        ParsingState.PATH -> {
          if ((i - lastPathSegmentStart) > FILENAME_MAX) {
            startCanceledMode()
          }
          else when {
            line[i] == '\\' || line[i] == '/' -> {
              lastPathSegmentStart = i + 1
              if (i - pathStartIndex > 1){
                val currentCandidate = findValidResult(i, 0, 0)
                if (currentCandidate == null) {
                  /* This is not a valid path it means that continuing as a path no longer will result in a valid file, but this could be
                     the the start of a new path. Need to move back up to 4 characters since the path could include a drive letter.
                   */
                  if ((i - 3) > pathStartIndex && line[i - 1] == ':' && line[i - 2] in 'A'..'Z' && line[i - 3].isWhitespace()) {
                    i -= 5
                    startNormalMode()
                  }
                  else if ((i - 1) > pathStartIndex && line[i -1].isWhitespace()) {
                    i -= 2
                    startNormalMode()
                  }
                  else {
                    startCanceledMode()
                  }
                }
              }
            }
            line[i] == ':' -> {
              val previousI = i
              val longestCandidate = findValidResultWithNumbers(i)
              if (longestCandidate != null) {
                candidateItem = longestCandidate
              }
              else {
                // Could not parse numbers correctly (or is not a valid path, restore i)
                i = previousI
                // Could be the start of a windows path, move back in that case
                if (((i - 1) > pathStartIndex)
                    && (line[i - 1] in 'A'..'Z')
                    && ((i + 1) < line.length)
                    && (line[i + 1] == '/' || line[i + 1] == '\\')) {
                  i -= 2
                }
              }
              startNormalMode()
            }
            // Paths can have white spaces and links get cut early (https://issuetracker.google.com/issues/136242040)
            // Or can be a valid path but be the prefix of a longer path (for example "/work projects/" and "/work projects 2" exist,
            // https://issuetracker.google.com/issues/167701951)
            line[i].isWhitespace() -> {
              val possibleCandidate = findValidResult(i, 0, 0)
              if (possibleCandidate != null) {
                candidateItem = possibleCandidate
              }
            }
          }
        }
        ParsingState.CANCELED_PATH -> if (line[i].isWhitespace()) startNormalMode()
      }
      i++
    }
    if (candidateItem != null) {
      items += candidateItem!!
    }
    if (items.isEmpty()) return null
    return Filter.Result(items)
  }
}

private fun String?.safeToIntOrDefault(default: Int): Int = try {
  this?.toInt() ?: default
}
catch (e: NumberFormatException) {
  default
}

private fun String.takeWhileFromIndex(index: Int, predicate: (Char) -> Boolean): String? {
  for (i in index until length) {
    if (!predicate(get(i))) {
      return if (i == index) null else substring(index, i)
    }
  }
  return null
}

class GenericFileFilterProvider : ConsoleFilterProvider {
  override fun getDefaultFilters(project: Project): Array<GenericFileFilter> {
    return arrayOf(GenericFileFilter(project, LocalFileSystem.getInstance(), isEnabled = {
      AdvancedSettings.getBoolean("ide.enable.generic.file.filter")
    }))
  }
}
