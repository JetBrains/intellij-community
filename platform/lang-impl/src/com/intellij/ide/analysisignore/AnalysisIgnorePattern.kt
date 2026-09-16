// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * One pattern of a [`.analysisignore`][ANALYSIS_IGNORE_FILE_NAME] file.
 */
@ApiStatus.Internal
class AnalysisIgnorePattern internal constructor(
  // The line that this pattern comes from, without the trailing spaces.
  val source: String,
  // `true` for a pattern that ends with `/`. Such a pattern matches a directory only.
  val directoryOnly: Boolean,
  /**
   * `true` if the pattern holds a `/` at its start or in its middle. An anchored pattern matches a path below the directory of the file.
   * A pattern without such a `/` matches a name at any level below that directory.
   */
  val anchored: Boolean,
  private val matcher: (CharSequence) -> Boolean,
) {

  /**
   * Returns `true` if this pattern matches the path itself.
   */
  fun matches(relativePath: CharSequence, name: CharSequence, isDirectory: Boolean): Boolean {
    if (directoryOnly && !isDirectory) return false

    return matcher(if (anchored) relativePath else name)
  }

  companion object {
    /**
     * Returns the pattern that [line] holds, without the trailing spaces. Returns `null` for a blank line and for a comment.
     */
    fun patternSource(line: String): String? {
      val source = line.trimEnd(' ')
      return if (source.isEmpty() || source[0] == '#') null else source
    }

    /**
     * Returns whether the format supports [source]. The reader calls this for each pattern of a file and stores the supported ones only.
     */
    fun validate(source: String): AnalysisIgnoreValidated {
      unsupportedReason(source)?.let { return AnalysisIgnoreValidated.Unsupported(it) }
      if (parse(source).body.isEmpty()) return AnalysisIgnoreValidated.Unsupported(AnalysisIgnoreUnsupported.SeparatorsOnly)
      return AnalysisIgnoreValidated.Supported
    }

    /**
     * Compiles [source] for a file system with the given case sensitivity.
     */
    fun compile(source: String, caseSensitive: Boolean): AnalysisIgnorePattern {
      val parsed = parse(source)
      return AnalysisIgnorePattern(
        source = source,
        directoryOnly = parsed.directoryOnly,
        anchored = parsed.anchored,
        matcher = matcherOf(parsed.body, parsed.anchored, caseSensitive),
      )
    }

    fun compileAll(sources: List<String>, caseSensitive: Boolean): List<AnalysisIgnorePattern> = sources.map { compile(it, caseSensitive) }

    private fun unsupportedReason(source: String): AnalysisIgnoreUnsupported? {
      if (source.startsWith('!')) return AnalysisIgnoreUnsupported.Negation

      var i = 0
      while (i < source.length) {
        when (source[i]) {
          '[' -> return AnalysisIgnoreUnsupported.CharacterClass
          '\\' -> return AnalysisIgnoreUnsupported.Escape
          '*' -> {
            var end = i
            while (end < source.length && source[end] == '*') end++
            val length = end - i
            if (length > 2) return AnalysisIgnoreUnsupported.AsteriskRun(length)
            if (length == 2 && !source.fillsComponent(i, end)) return AnalysisIgnoreUnsupported.PartialDoubleAsterisk
            i = end
          }
          else -> i++
        }
      }
      return null
    }

    /**
     * Returns the parts of [source] that do not depend on the case sensitivity of a file system.
     */
    private fun parse(source: String): Parsed {
      var body = source

      val directoryOnly = body.endsWith('/')
      if (directoryOnly) {
        body = body.dropLast(1)
      }

      var anchored = body.contains('/')
      if (anchored && body[0] == '/') {
        body = body.substring(1)
      }

      if (body.startsWith("**/") && body.indexOf('/', 3) < 0) {
        body = body.substring(3)
        anchored = false
      }

      return Parsed(directoryOnly = directoryOnly, anchored = anchored, body = body)
    }
  }
}


/**
 * The result of [AnalysisIgnorePattern.validate] for one pattern of a [`.analysisignore`][ANALYSIS_IGNORE_FILE_NAME] file.
 */
@ApiStatus.Internal
sealed interface AnalysisIgnoreValidated {
  // The format refuses the pattern. [reason] says what the pattern holds.
  data class Unsupported(val reason: AnalysisIgnoreUnsupported) : AnalysisIgnoreValidated

  // The format supports the pattern, and the reader stores it.
  object Supported : AnalysisIgnoreValidated
}


@ApiStatus.Internal
sealed interface AnalysisIgnoreUnsupported {
  // The text of this reason for the log. It is not a string for the user.
  val message: String

  // A leading `!`.
  object Negation : AnalysisIgnoreUnsupported {
    override val message: String get() = "a negation"
  }

  // A `[`. It opens a class of characters
  object CharacterClass : AnalysisIgnoreUnsupported {
    override val message: String get() = "a class of characters"
  }

  // A `\`. It takes the next character as itself.
  object Escape : AnalysisIgnoreUnsupported {
    override val message: String get() = "an escape"
  }

  // A run of [length] asterisks. This format holds only `*` and `**`.
  data class AsteriskRun(val length: Int) : AnalysisIgnoreUnsupported {
    override val message: String get() = "a run of $length asterisks"
  }

  // A `**` that shares its path component with another character, as in `a**b`.
  object PartialDoubleAsterisk : AnalysisIgnoreUnsupported {
    override val message: String get() = "a '**' that fills no whole component of the path"
  }

  // A line of `/` characters only, as `/` and `//`. Such a line names the directory of the file itself and no path below it.
  object SeparatorsOnly : AnalysisIgnoreUnsupported {
    override val message: String get() = "a '/' and nothing else"
  }
}


/**
 * The parts of a pattern that do not depend on the case sensitivity of a file system. [body] is empty when the separators alone remain,
 * as for `/`.
 */
private class Parsed(
  val directoryOnly: Boolean,
  val anchored: Boolean,
  val body: String,
)


private val LOG = logger<AnalysisIgnorePattern>()

/**
 * Returns the test of the pattern [body].
 */
private fun matcherOf(body: String, anchored: Boolean, caseSensitive: Boolean): (CharSequence) -> Boolean {
  val ignoreCase = !caseSensitive
  if (body.hasNoWildcard()) {
    return { text -> text.contentEquals(body, ignoreCase) }
  }
  if (!anchored && body.isSuffixMask()) {
    val suffix = body.substring(1)
    return { text -> text.endsWith(suffix, ignoreCase) }
  }

  val regex = regexOf(body, caseSensitive) ?: return { false }
  return { text -> regex.matcher(text).matches() }
}

private fun regexOf(body: String, caseSensitive: Boolean): Pattern? {
  val regexText = StringBuilder(body.length * 2)
  var i = 0
  while (i < body.length) {
    val c = body[i]
    when (c) {
        '*' -> {
          var end = i
          while (end < body.length && body[end] == '*') end++
          i = regexText.appendAsterisksOf(body, i, end)
        }
        '?' -> {
          regexText.append("[^/]")
          i++
        }
        else -> {
          regexText.appendLiteral(c)
          i++
        }
    }
  }

  val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
  return try {
    Pattern.compile(regexText.toString(), flags)
  }
  catch (e: PatternSyntaxException) {
    LOG.error("Cannot compile the regex '$regexText' of a $ANALYSIS_IGNORE_FILE_NAME pattern", e)
    null
  }
}

private const val REGEX_METACHARACTERS: String = "\\^\$.|?*+()[]{}"

/** Returns `true` if the run of the receiver in `[start, end)` fills a whole component of the path. */
private fun String.fillsComponent(start: Int, end: Int): Boolean =
  (start == 0 || this[start - 1] == '/') && (end == length || this[end] == '/')

private fun String.hasNoWildcard(): Boolean = none { it == '*' || it == '?' }

/** Returns `true` if the receiver is one `*` followed by characters that mean themselves, as `*.log` is. */
private fun String.isSuffixMask(): Boolean = length > 1 && this[0] == '*' && indexOf('*', 1) < 0 && indexOf('?') < 0

private fun StringBuilder.appendAsterisksOf(body: String, start: Int, end: Int): Int {
  if (end - start == 1) {
    append("[^/]*")
    return end
  }

  if (end == body.length) {
    // A trailing '/**' matches everything below the component before it.
    append(".*")
    return end
  }

  // A '**/' matches zero or more components.
  append("(?:.*/)?")
  return end + 1
}

private fun StringBuilder.appendLiteral(c: Char) {
  if (c in REGEX_METACHARACTERS) append('\\')
  append(c)
}
