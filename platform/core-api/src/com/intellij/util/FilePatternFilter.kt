// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import java.util.regex.Pattern

interface FilePatternFilter {
  fun pathMatches(path: CharSequence): Boolean

  companion object {

    @JvmStatic
    fun parseFilter(
      filterText: CharSequence?,
      andDelimiter: String = "&",
      orDelimiter: String = "|",
      patternFlags: Int = 0
    ): FilePatternFilter {
      if (filterText.isNullOrBlank()) return AlwaysTrueFilter
      if (filterText.contains(andDelimiter)) {
        val filters = filterText.split(andDelimiter).map { it.trim() }.filter { it.isNotEmpty() }.map {
          parseFilter(it, andDelimiter, orDelimiter, patternFlags)
        }
        return if (filters.size == 1) {
          filters[0]
        }
        else {
          IntersectionFilter(filters)
        }
      }
      else {
        val filters: List<FilePatternFilter> = filterText.split(orDelimiter).map {
          val trimmed = it.trim()
          if (trimmed.startsWith('!')) {
            ExcludeFilePatternFilter(trimmed.substring(1), patternFlags)
          }
          else {
            IncludeFilePatternFilter(trimmed, patternFlags)
          }
        }
        return if (filters.size == 1) {
          filters[0]
        }
        else {
          UnionFilter(filters)
        }
      }
    }
  }
}

open class IncludeFilePatternFilter(filter: String, flags: Int) : FilePatternFilter {
  private val pattern: Pattern = PatternUtil.fromMask(filter, flags)

  override fun pathMatches(path: CharSequence): Boolean = pattern.matcher(path).matches()

}

class ExcludeFilePatternFilter(filter: String, flags: Int) : FilePatternFilter {
  private val pattern: Pattern = PatternUtil.fromMask(filter, flags)

  override fun pathMatches(path: CharSequence): Boolean = !pattern.matcher(path).matches()
}

class IntersectionFilter(private val filters: List<FilePatternFilter>) : FilePatternFilter {
  override fun pathMatches(path: CharSequence): Boolean = filters.all { it.pathMatches(path) }
}

class UnionFilter(private val filters: List<FilePatternFilter>) : FilePatternFilter {
  override fun pathMatches(path: CharSequence): Boolean = filters.any { it.pathMatches(path) }
}

object AlwaysTrueFilter : FilePatternFilter {
  override fun pathMatches(path: CharSequence): Boolean = true
}