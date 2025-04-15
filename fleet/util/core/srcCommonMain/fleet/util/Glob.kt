// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

// copied from org.ec4j.core.model.Glob
fun convertGlobToRegEx(globString: String, ranges: MutableList<IntArray>, result: StringBuilder) {
  val length = globString.length
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
        if (i < length && globString[i] == '*') {
          result.append(".*")
          i++
        }
        else {
          result.append("[^/]*")
        }
      }
      current == '?' -> result.append(".")
      current == '[' -> {
        val seenSlash = findChar('/', ']', globString, length, i) >= 0
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
      current == '/' -> {
        if (i < length && globString[i] == '*') {
          if (i + 1 < length && globString[i + 1] == '*' && i + 2 < length && globString[i + 2] == '/') {
            result.append("(?:/|/.*/)")
            i += 3
          }
          else {
            result.append(current)
          }
        }
        else {
          result.append(current)
        }
      }
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

    if (current == '\\') {
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
