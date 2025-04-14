// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

/**
 * Splits a single parameter string into a list of parameters.
 *
 * The function splits the parameters using the following rules:
 * - Trims any starting or trailing white spaces in the parameter string.
 * - Parameters are split apart by white spaces, the white spaces are dropped.
 * - Parameters encapsulated with double quotes are treated as a single parameter.
 * - Double quotes are dropped while escaped double quotes (represented as `&#92;"`) are unescaped.
 * - Supports single quotes if the `supportSingleQuotes` parameter is set to true.
 *
 * Notes:
 *    If a parameter is wrapped with quotes (double or single, if supported), its content will be
 *      treated as a single parameter, including spaces.
 *    If `supportSingleQuotes` and `keepQuotes` are set to true, single quotes in the parameters will be retained.
 *
 * Examples:
 *    parseParameters('a "1 2" b') returns ['a', '1 2', 'b']
 *    parseParameters('"a &#92;"1 2&#92;"" b') returns ['a "1 2"', 'b']
 *
 * @param parameterString The String holding the parameters to be parsed.
 * @param keepQuotes Specifies whether to keep any quotes found in the parameter string. True by default.
 * @param supportSingleQuotes Allows the handling of single quotes in the parameter string when true. True by default.
 * @param keepEmptyParameters Keeps any empty parameters that may exist in the parameter string. True by default.
 *
 * @return Returns a List of Strings, where each item in the list represents a parameter from the parameter string.
 *
 * @see com.intellij.util.execution.ParametersListUtil.parse
 */
fun parseParameters(parameterString: String,
                    keepQuotes: Boolean = true,
                    supportSingleQuotes: Boolean = true,
                    keepEmptyParameters: Boolean = true): List<String> {
  var paramsStr = parameterString
  if (!keepEmptyParameters) {
    paramsStr = paramsStr.trim()
  }
  val params = mutableListOf<String>()
  if (paramsStr.isEmpty()) {
    return params
  }
  val token = StringBuilder(128)
  var inQuotes = false
  var escapedQuote = false
  val possibleQuoteChars = HashSet<Int>()
  possibleQuoteChars.add('"'.code)
  if (supportSingleQuotes) {
    possibleQuoteChars.add('\''.code)
  }
  var currentQuote = 0.toChar()
  var nonEmpty = false
  for (i in paramsStr.indices) {
    val ch = paramsStr[i]
    if (if (inQuotes) currentQuote == ch else possibleQuoteChars.contains(ch.code)) {
      if (!escapedQuote) {
        inQuotes = !inQuotes
        currentQuote = ch
        nonEmpty = true
        if (!keepQuotes) {
          continue
        }
      }
      escapedQuote = false
    }
    else if (ch.isWhitespace()) {
      if (!inQuotes) {
        if (keepEmptyParameters || token.isNotEmpty() || nonEmpty) {
          params.add(token.toString())
          token.setLength(0)
          nonEmpty = false
        }
        continue
      }
    }
    else if (ch == '\\' && i < paramsStr.length - 1) {
      val nextChar = paramsStr[i + 1]
      if (if (inQuotes) currentQuote == nextChar else possibleQuoteChars.contains(nextChar.code)) {
        escapedQuote = true
        if (!keepQuotes) {
          continue
        }
      }
    }
    token.append(ch)
  }
  if (keepEmptyParameters || token.isNotEmpty() || nonEmpty) {
    params.add(token.toString())
  }
  return params
}