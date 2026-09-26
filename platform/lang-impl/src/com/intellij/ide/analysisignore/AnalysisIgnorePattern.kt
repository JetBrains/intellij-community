// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
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
  /**
   * The path below the directory of the file that an anchored pattern without a wildcard names, with a `/` between its components.
   * `null` for every other pattern.
   */
  val literalPath: String?,
  private val segments: List<AnalysisIgnoreSegment>,
) {

  // `true` if a segment is a `**`. Such a pattern matches paths of several depths, and thus it needs the whole path of a file.
  internal val hasAnySegments: Boolean = segments.any { it === AnalysisIgnoreSegment.AnySegments }

  // The count of the segments. An anchored pattern without a `**` matches a path of this depth only.
  internal val depth: Int
    get() = segments.size

  /**
   * Returns `true` if this pattern matches the path itself.
   */
  fun matches(relativePath: CharSequence, name: CharSequence, isDirectory: Boolean): Boolean {
    if (directoryOnly && !isDirectory) return false
    if (!anchored) return segments[0].matches(name)

    val names = relativePath.split('/')
    return matchesSegments(names.size) { segment, index -> segment.matches(names[index]) }
  }

  /**
   *  Returns `true` if this pattern without a slash matches the name of [node].
   */
  internal fun matchesName(node: AnalysisIgnoreNode): Boolean {
    if (directoryOnly && !node.isDirectory) return false
    return segments[0].matches(node)
  }

  internal fun matchesUpward(node: AnalysisIgnoreNode): Boolean {
    if (directoryOnly && !node.isDirectory) return false
    if (!segments[segments.lastIndex].matches(node)) return false

    var current = node.file
    for (i in segments.lastIndex - 1 downTo 0) {
      current = current.parent ?: return false
      if (!segments[i].matches(AnalysisIgnoreNode(current))) return false
    }
    return true
  }

  internal fun matchesChain(chain: List<AnalysisIgnoreNode>): Boolean {
    if (directoryOnly && !chain[chain.lastIndex].isDirectory) return false
    return matchesSegments(chain.size) { segment, index -> segment.matches(chain[index]) }
  }

  private inline fun matchesSegments(pathLength: Int, matchesAt: (AnalysisIgnoreSegment, Int) -> Boolean): Boolean {
    var segmentIndex = 0
    var pathIndex = 0
    var lastAnyIndex = -1
    var lastAnyPathIndex = -1
    while (pathIndex < pathLength) {
      if (segmentIndex < segments.size) {
        val segment = segments[segmentIndex]
        if (segment === AnalysisIgnoreSegment.AnySegments) {
          lastAnyIndex = segmentIndex
          lastAnyPathIndex = pathIndex
          segmentIndex++
          continue
        }
        if (matchesAt(segment, pathIndex)) {
          segmentIndex++
          pathIndex++
          continue
        }
      }
      if (lastAnyIndex < 0) return false
      segmentIndex = lastAnyIndex + 1
      lastAnyPathIndex++
      pathIndex = lastAnyPathIndex
    }
    while (segmentIndex < segments.size && segments[segmentIndex] === AnalysisIgnoreSegment.AnySegments) segmentIndex++
    return segmentIndex == segments.size
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
        literalPath = literalPathOf(parsed.body, parsed.anchored),
        segments = segmentsOf(parsed.body, parsed.anchored, caseSensitive),
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
 * One component of a pattern. It matches one component of a path, apart from [AnySegments], which takes zero or more of them.
 */
internal sealed interface AnalysisIgnoreSegment {
  /** Returns `true` if this segment matches [name], one component of a path. */
  fun matches(name: CharSequence): Boolean

  /** Returns `true` if this segment matches the name of [node]. */
  fun matches(node: AnalysisIgnoreNode): Boolean = matches(node.name)

  /** A component without a wildcard. It compares the id of a name first, which reads no string from the VFS. */
  class Literal(private val text: String, private val ignoreCase: Boolean) : AnalysisIgnoreSegment {
    // The id of [text] in the names of the VFS, or [NO_NAME_ID] before the first comparison with a file of the VFS. Two threads can store
    // the name at the same time and get the same id.
    @Volatile
    private var textNameId: Int = NO_NAME_ID

    override fun matches(name: CharSequence): Boolean = name.contentEquals(text, ignoreCase)

    override fun matches(node: AnalysisIgnoreNode): Boolean {
      val nameId = node.nameId
      if (nameId == NO_NAME_ID) return matches(node.name)

      // Equal ids mean equal names. Different ids mean different names on a case-sensitive file system only.
      if (nameId == textNameId()) return true
      return ignoreCase && matches(node.name)
    }

    private fun textNameId(): Int {
      var id = textNameId
      if (id == NO_NAME_ID) {
        id = VirtualFileManager.getInstance().storeName(text)
        textNameId = id
      }
      return id
    }
  }

  /** A component of one leading `*` and a tail without a wildcard, as `*.log`. */
  class Suffix(private val suffix: String, private val ignoreCase: Boolean) : AnalysisIgnoreSegment {
    override fun matches(name: CharSequence): Boolean = name.endsWith(suffix, ignoreCase)
  }

  // A component with a wildcard.
  class Glob(private val regex: Pattern) : AnalysisIgnoreSegment {
    override fun matches(name: CharSequence): Boolean = regex.matcher(name).matches()
  }

  /** A `*` alone. It matches every name. */
  object AnyName : AnalysisIgnoreSegment {
    override fun matches(name: CharSequence): Boolean = true
  }

  /** A component that matches no name. */
  object NoName : AnalysisIgnoreSegment {
    override fun matches(name: CharSequence): Boolean = false
  }

  /** A `**` that fills a component. */
  object AnySegments : AnalysisIgnoreSegment {
    override fun matches(name: CharSequence): Boolean = false
  }
}


internal class AnalysisIgnoreNode(val file: VirtualFile) {
  val isDirectory: Boolean = file.isDirectory

  private var nameIdRead = false
  private var nameIdValue = NO_NAME_ID
  private var nameValue: CharSequence? = null

  /** The id of the name of the file in the names of the VFS, or [NO_NAME_ID] for a file outside the VFS. */
  val nameId: Int
    get() {
      if (!nameIdRead) {
        nameIdValue = (file as? VirtualFileSystemEntry)?.nameId ?: NO_NAME_ID
        nameIdRead = true
      }
      return nameIdValue
    }

  val name: CharSequence
    get() = nameValue ?: file.nameSequence.also { nameValue = it }
}

internal const val NO_NAME_ID: Int = -1


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
 * Returns [body] if the anchored pattern names one path: no wildcard, and no component that is empty, `.` or `..`. Returns `null`
 * otherwise.
 */
private fun literalPathOf(body: String, anchored: Boolean): String? {
  if (!anchored || !body.hasNoWildcard()) return null
  if (body.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
  return body
}

/**
 * Returns the segments of the pattern [body].
 */
private fun segmentsOf(body: String, anchored: Boolean, caseSensitive: Boolean): List<AnalysisIgnoreSegment> {
  val ignoreCase = !caseSensitive
  if (!anchored) return listOf(nameSegmentOf(body, ignoreCase))

  val components = body.split('/')
  val segments = ArrayList<AnalysisIgnoreSegment>(components.size + 1)
  for (component in components) {
    segments.add(if (component == "**") AnalysisIgnoreSegment.AnySegments else nameSegmentOf(component, ignoreCase))
  }
  if (segments[segments.lastIndex] === AnalysisIgnoreSegment.AnySegments) {
    segments.add(AnalysisIgnoreSegment.AnyName)
  }
  return segments
}

/**
 * Returns the segment of one [component] of a path. The cheaper segments come first, and a regex is the last resort.
 */
private fun nameSegmentOf(component: String, ignoreCase: Boolean): AnalysisIgnoreSegment = when {
  component.isEmpty() -> AnalysisIgnoreSegment.NoName
  component == "*" || component == "**" -> AnalysisIgnoreSegment.AnyName
  component.hasNoWildcard() -> AnalysisIgnoreSegment.Literal(component, ignoreCase)
  component.isSuffixMask() -> AnalysisIgnoreSegment.Suffix(component.substring(1), ignoreCase)
  else -> regexOf(component, ignoreCase)?.let { AnalysisIgnoreSegment.Glob(it) } ?: AnalysisIgnoreSegment.NoName
}

/**
 * Returns the regex of one [component] of a path. A `*` and a `?` stop at a slash, and every other character means itself.
 */
private fun regexOf(component: String, ignoreCase: Boolean): Pattern? {
  val regexText = StringBuilder(component.length * 2)
  for (c in component) {
    when (c) {
      '*' -> regexText.append("[^/]*")
      '?' -> regexText.append("[^/]")
      else -> regexText.appendLiteral(c)
    }
  }

  val flags = if (ignoreCase) Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE else 0
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

private fun StringBuilder.appendLiteral(c: Char) {
  if (c in REGEX_METACHARACTERS) append('\\')
  append(c)
}
