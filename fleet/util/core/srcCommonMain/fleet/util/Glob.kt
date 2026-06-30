// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

// Converts a glob into a regex string. Copied from org.ec4j.core.model.Glob, then generalized to be
// path-separator aware. This is the shared glob engine behind globToRegex, EditorConfig matching, and
// buildPathGlobPattern (which only adds relativization, case-insensitivity, and dir-only filtering).
//
// [pathSeparator] is the path separator (default '/'). '*' and '?' match within a single segment and do
// not cross it; only a standalone "**" component crosses separators, matching zero or more segments.
// '[...]' classes and '{a,b}' / '{n..m}' brace expansions are supported. '\' is the escape character,
// unless it is itself the separator (Windows), where escaping is disabled. See GlobTest for the cases.
fun convertGlobToRegEx(globString: String, ranges: MutableList<IntArray>, result: StringBuilder, pathSeparator: Char = '/') {
  val length = globString.length
  val sep = if (pathSeparator == '/') "/" else Regex.escape(pathSeparator.toString())
  val notSep = "[^$sep]"
  val backslashIsEscape = pathSeparator != '\\'
  var i = 0
  var braceLevel = 0
  val matchingBraces = globString.matchingBraces()
  var escaped = false
  var inBrackets = false
  while (i < length) {
    val current = globString[i]
    i++
    when {
      current == '*' -> {
        val doubleAsterisk = i < length && globString[i] == '*'
        val atComponentStart = (i - 1) == 0 || globString[i - 2] == pathSeparator
        val atComponentEnd = (i + 1) >= length || globString[i + 1] == pathSeparator
        when {
          doubleAsterisk && atComponentStart && atComponentEnd -> {
            // a standalone "**" path component matches across separators
            if (globString.getOrNull(i + 1) == null || globString.getOrNull(i + 2) == null) {
              // "**" at the end, or followed only by a trailing separator
              result.append(".*")
              i++
            }
            else {
              // "**<sep>" in the middle: match any prefix path ending at a separator, or nothing.
              // ".*" (not "[^sep]+") so it also crosses a leading separator on absolute paths (see FSD excludes).
              result.append("(?:.*$sep)?")
              i += 2
            }
          }
          (i - 1) == 0 || globString[i - 2] != '*' -> {
            // a single '*' (or the first '*' of a non-standalone "**") does not cross separators
            result.append("$notSep*")
          }
          // a subsequent '*' of a non-standalone "**" run is collapsed into the previous "[^sep]*"
        }
      }
      current == '?' -> result.append(notSep)
      current == '[' -> {
        val seenSlash = findChar(pathSeparator, ']', globString, length, i) >= 0
        if (seenSlash || escaped) {
          result.append("\\[")
        }
        else if (i < length && "!^".indexOf(globString[i]) >= 0) {
          i++
          result.append("[^")
        }
        else {
          result.append("[")
        }
        inBrackets = true
      }
      current == ']' || (current == '-' && inBrackets) -> {
        if (escaped) {
          result.append("\\")
        }
        result.append(current)
        inBrackets = current != ']' || escaped
      }
      current == '{' -> {
        val j = findChar(',', '}', globString, length, i)
        if (j < 0 && -j < length) {
          val choice = globString.substring(i, -j)
          val range = getNumericRange(choice)
          if (range != null) {
            result.append("(\\d+)")
            ranges.add(range)
          }
          else {
            result.append("\\{")
            convertGlobToRegEx(choice, ranges, result)
            result.append("\\}")
          }
          i = -j + 1
        }
        else if (matchingBraces) {
          result.append("(?:")
          braceLevel++
        }
        else {
          result.append("\\{")
        }
      }
      current == ',' -> result.append(if (braceLevel > 0 && !escaped) "|" else ",")
      // the separator is appended as-is; "**" handling (above) absorbs a trailing separator
      current == pathSeparator -> result.append(sep)
      current == '}' -> {
        if (braceLevel > 0 && !escaped) {
          result.append(")")
          braceLevel--
        }
        else {
          result.append("}")
        }
      }
      current != '\\' -> result.escapeToRegex(current)
    }

    if (backslashIsEscape && current == '\\') {
      if (escaped) result.append("\\\\")
      escaped = !escaped
    }
    else {
      escaped = false
    }
  }
}

private fun String.matchingBraces(): Boolean {
  var i = 0
  val len = length
  var openedCount = 0
  while (i < len) {
    when (get(i++)) {
      '\\' -> i++
      '{' -> openedCount++
      '}' -> openedCount--
      else -> {}
    }
  }
  return openedCount == 0
}

private fun getNumericRange(choice: String): IntArray? =
  choice.indexOf("..").takeIf { it >= 0 }?.let { separator ->
    try {
      val start = choice.substring(0, separator).toInt()
      val end = choice.substring(separator + 2).toInt()
      intArrayOf(start, end)
    }
    catch (_: NumberFormatException) {
      null
    }
  }


private fun findChar(c: Char, stopAt: Char, pattern: String, length: Int, start: Int): Int {
  var j = start
  var escapedChar = false
  while (j < length && (pattern[j] != stopAt || escapedChar)) {
    if (pattern[j] == c && !escapedChar) {
      return j
    }
    escapedChar = pattern[j] == '\\' && !escapedChar
    j++
  }
  return -j
}

private fun StringBuilder.escapeToRegex(c: Char) {
  when {
    c == ' ' || c.isLetter() || c.isDigit() || c == '_' || c == '-' -> append(c)
    c == '\n' -> append("\\n")
    else -> append('\\').append(c)
  }
}
