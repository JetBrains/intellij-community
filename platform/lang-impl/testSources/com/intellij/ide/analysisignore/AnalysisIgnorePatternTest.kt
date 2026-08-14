// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The format of a `.analysisignore` file, which is a subset of the format of a `.gitignore` file. The subset holds `*`, `?` and `**`,
 * and it leaves out the negation, the class of characters and the escape. Every expectation of a pattern that the subset holds is what
 * `git check-ignore` answers for the same pattern and the same path.
 */
class AnalysisIgnorePatternTest {

  // -----------------------------------------------------------------------------------------------------------------------------------
  // Where a pattern matches
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  fun `a pattern without a slash matches at any level`() {
    val matcher = matcherOf("build")

    assertTrue(matcher.excludes("build"))
    assertTrue(matcher.excludes("sub/build"))
    assertTrue(matcher.excludes("a/b/c/build"))
    // Everything below an excluded directory goes with it.
    assertTrue(matcher.excludes("build/keep", isDirectory = false))
    assertFalse(matcher.excludes("builds"))
  }

  @Test
  fun `a slash in the middle ties the pattern to the directory of the file`() {
    val matcher = matcherOf("doc/frotz/")

    // The example of the documentation of git: 'doc/frotz/' matches 'doc/frotz' and not 'a/doc/frotz'.
    assertTrue(matcher.excludes("doc/frotz"))
    assertFalse(matcher.excludes("a/doc/frotz"))
  }

  @Test
  fun `a leading slash ties the pattern to the directory of the file`() {
    val matcher = matcherOf("/build")

    assertTrue(matcher.excludes("build"))
    assertFalse(matcher.excludes("sub/build"))
  }

  @Test
  fun `a pattern that ends with a slash matches a directory only`() {
    val matcher = matcherOf("frotz/")

    // The other half of the example of git: 'frotz/' matches 'frotz' and 'a/frotz', as long as they are directories.
    assertTrue(matcher.excludes("frotz"))
    assertTrue(matcher.excludes("a/frotz"))
    assertFalse(matcher.excludes("frotz", isDirectory = false))
    assertFalse(matcher.excludes("a/frotz", isDirectory = false))
  }

  @Test
  fun `a pattern without a trailing slash matches a file and a directory alike`() {
    val matcher = matcherOf("out")

    assertTrue(matcher.excludes("out"))
    assertTrue(matcher.excludes("out", isDirectory = false))
  }

  @Test
  fun `the directory of the file itself is never excluded`() {
    // A '*' takes everything below the directory that declares it, and that directory is not below itself.
    assertFalse(matcherOf("*").isExcluded("", isDirectory = true))
    assertFalse(matcherOf("**").isExcluded("", isDirectory = true))
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // The wildcards
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  fun `an asterisk matches anything but a slash`() {
    val anyLog = matcherOf("*.log")
    assertTrue(anyLog.excludes("a.log", isDirectory = false))
    assertTrue(anyLog.excludes("sub/a.log", isDirectory = false))

    val logsOfDocs = matcherOf("docs/*.log")
    assertTrue(logsOfDocs.excludes("docs/a.log", isDirectory = false))
    // The asterisk stops at the slash, and thus this one names a file of 'docs' and not of a directory below it.
    assertFalse(logsOfDocs.excludes("docs/sub/a.log", isDirectory = false))
  }

  @Test
  fun `a question mark matches one character but a slash`() {
    val matcher = matcherOf("?.log")

    assertTrue(matcher.excludes("a.log", isDirectory = false))
    assertFalse(matcher.excludes("ab.log", isDirectory = false))
    assertFalse(matcher.excludes(".log", isDirectory = false))
  }

  @Test
  fun `a leading double asterisk matches in all directories`() {
    // The documentation of git: '**/foo' matches 'foo' anywhere, the same as the pattern 'foo'.
    val foo = matcherOf("**/foo")
    assertTrue(foo.excludes("foo"))
    assertTrue(foo.excludes("a/foo"))
    assertTrue(foo.excludes("a/b/foo"))

    // '**/foo/bar' matches 'bar' anywhere that is directly under 'foo'.
    val fooBar = matcherOf("**/foo/bar")
    assertTrue(fooBar.excludes("foo/bar"))
    assertTrue(fooBar.excludes("a/foo/bar"))
    assertFalse(fooBar.excludes("a/foo/b/bar"))
    assertFalse(fooBar.excludes("bar"))
  }

  @Test
  fun `a trailing double asterisk matches everything inside`() {
    val matcher = matcherOf("abc/**")

    assertTrue(matcher.excludes("abc/x"))
    assertTrue(matcher.excludes("abc/x/y"))
    // 'abc' itself stays: the pattern names what is inside it.
    assertFalse(matcher.excludes("abc"))
  }

  @Test
  fun `a double asterisk between two slashes matches zero or more directories`() {
    val matcher = matcherOf("a/**/b")

    assertTrue(matcher.excludes("a/b"))
    assertTrue(matcher.excludes("a/x/b"))
    assertTrue(matcher.excludes("a/x/y/b"))
    assertFalse(matcher.excludes("b"))
    assertFalse(matcher.excludes("x/a/b"))
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // The lines that the format leaves out
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  fun `a line that the format leaves out matches nothing`() {
    // The negation, the class of characters, the escape, and a run of asterisks that fills no whole component of the path. A '.gitignore'
    // file matches each of these, and this format names the line in the log and matches nothing.
    val lines = listOf(
      "!keep.log", "!/build", "[a-z].txt", "[!0-9].txt", "[[:digit:]].txt", "[]a].txt", """nested\dir""", """foo\""", """\*literal""",
      "a**b", "a***b", "**a", "a**", "a/***/b",
    )

    for (line in lines) {
      assertNotNull(reasonOf(line), line)
      assertEquals(emptyList<String>(), storedSourcesOf(line), line)
      assertFalse(matcherOf(line).excludes(line), line)
    }
  }

  @Test
  fun `each line that the format leaves out names its own reason`() {
    assertEquals(AnalysisIgnoreUnsupported.Negation, reasonOf("!keep.log"))
    assertEquals(AnalysisIgnoreUnsupported.CharacterClass, reasonOf("[a-z].txt"))
    assertEquals(AnalysisIgnoreUnsupported.Escape, reasonOf("""nested\dir"""))
    // The count tells one run of asterisks from another, which a boolean could not do.
    assertEquals(AnalysisIgnoreUnsupported.AsteriskRun(3), reasonOf("a***b"))
    assertEquals(AnalysisIgnoreUnsupported.PartialDoubleAsterisk, reasonOf("a**b"))
    // The '!' of this line is not its first character, and thus the class of characters answers first.
    assertEquals(AnalysisIgnoreUnsupported.CharacterClass, reasonOf("[!0-9].txt"))
  }

  @Test
  fun `the format holds every wildcard that it names`() {
    for (line in listOf("*", "**", "*.log", "a?c", "a*b", "**/foo", "a/**/b", "abc/**", "/**", "build/", "docs/gen", "a]b")) {
      assertEquals(null, reasonOf(line), line)
      assertEquals(line, AnalysisIgnorePattern.compile(line, caseSensitive = true).source, line)
    }
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // The lines of the file
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  fun `a blank line and a comment line hold no pattern`() {
    assertEquals(emptyList<String>(), sourcesOf("", "   ", "# a comment", "#"))
  }

  @Test
  fun `only the trailing spaces of a line go`() {
    assertEquals(listOf("build", "  build", "  # note"), sourcesOf("build  ", "  build", "  # note"))

    // A leading space is part of the pattern, and thus an indented line names a path that starts with a space, and an indented '#' is a
    // pattern and not a comment.
    assertFalse(matcherOf("  build").excludes("build"))
    assertTrue(matcherOf("  build").excludes("  build"))
  }

  @Test
  fun `a tab is no trailing space`() {
    // git trims a trailing ' ' and nothing else, and thus this pattern names a file whose name ends with a tab, and a line of one tab is a
    // pattern of its own and not a blank line.
    assertEquals(listOf("build\t", "\t"), sourcesOf("build\t", "\t"))
    assertFalse(matcherOf("build\t").excludes("build"))
    assertTrue(matcherOf("build\t").excludes("build\t"))
  }

  @Test
  fun `the line number of a pattern counts every line of the file`() {
    // A warning of the log names the line that the user wrote. The count therefore includes the empty lines and the comment lines, the
    // first line is 1, and a CRLF break counts as one break. AnalysisIgnoreFileReader counts the lines of a file this way.
    val text = "# a comment\r\n\r\n  build/tmp  \r\n!keep\r\n"

    assertEquals(listOf(3 to "  build/tmp", 4 to "!keep"), numberedSourcesOf(text))
  }

  @Test
  fun `a line that names the directory of the file holds no pattern`() {
    // The one gate names such a line in the log, and thus a user who writes it learns that it does nothing. A version of this that let the
    // line through stored it in the entity, where it excluded nothing at all.
    assertEquals(AnalysisIgnoreUnsupported.SeparatorsOnly, reasonOf("/"))
    assertEquals(AnalysisIgnoreUnsupported.SeparatorsOnly, reasonOf("//"))
    assertEquals(emptyList<String>(), storedSourcesOf("/"))
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // The file system
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  fun `a case-insensitive file system tells no case apart`() {
    val caseSensitive = matcherOf("Build/", caseSensitive = true)
    assertTrue(caseSensitive.excludes("Build"))
    assertFalse(caseSensitive.excludes("build"))

    // What git does with 'core.ignorecase', which is on by default on Windows and on the usual file system of macOS.
    val caseInsensitive = matcherOf("Build/", caseSensitive = false)
    assertTrue(caseInsensitive.excludes("Build"))
    assertTrue(caseInsensitive.excludes("build"))
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // The tiers of the matcher
  // -----------------------------------------------------------------------------------------------------------------------------------
  //
  // A regex is the last resort. Every expectation here holds whichever tier the pattern picks, and thus these tests say that the cheaper
  // tiers answer what the regex answered.

  @Test
  fun `a name without a wildcard is compared as it is`() {
    val matcher = matcherOf("build")

    assertTrue(matcher.excludes("build"))
    assertFalse(matcher.excludes("Build"))
    assertFalse(matcher.excludes("builds"))
    assertFalse(matcher.excludes("rebuild"))
  }

  @Test
  fun `one asterisk at either end matches a prefix, a suffix or an infix`() {
    val suffix = matcherOf("*.log")
    assertTrue(suffix.excludes("a.log", isDirectory = false))
    // An asterisk matches nothing at all as well as something.
    assertTrue(suffix.excludes(".log", isDirectory = false))
    assertFalse(suffix.excludes("a.LOG", isDirectory = false))
    assertFalse(suffix.excludes("log", isDirectory = false))

    val prefix = matcherOf("pre*")
    assertTrue(prefix.excludes("pre"))
    assertTrue(prefix.excludes("prefix"))
    assertFalse(prefix.excludes("Prefix"))
    assertFalse(prefix.excludes("apre"))

    val infix = matcherOf("*mid*")
    assertTrue(infix.excludes("mid"))
    assertTrue(infix.excludes("amidb"))
    assertFalse(infix.excludes("aMIDb"))
    assertFalse(infix.excludes("mad"))
  }

  @Test
  fun `an anchored path without a wildcard is compared as it is`() {
    val matcher = matcherOf("docs/generated/")

    assertTrue(matcher.excludes("docs/generated"))
    assertFalse(matcher.excludes("docs/Generated"))
    assertFalse(matcher.excludes("a/docs/generated"))
    assertFalse(matcher.excludes("docs/generated2"))
  }

  @Test
  fun `a leading double asterisk before one name matches by that name`() {
    for (pattern in listOf("**/foo", "/**/foo")) {
      val compiled = AnalysisIgnorePattern.compile(pattern, caseSensitive = true)
      assertFalse(compiled.anchored, pattern)

      val matcher = matcherOf(pattern)
      assertTrue(matcher.excludes("foo"), pattern)
      assertTrue(matcher.excludes("a/b/foo"), pattern)
      assertFalse(matcher.excludes("foobar"), pattern)
    }
  }

  @Test
  fun `a pattern of asterisks alone takes every name`() {
    for (pattern in listOf("*", "**")) {
      val matcher = matcherOf(pattern)

      assertTrue(matcher.excludes("anything"), pattern)
      assertTrue(matcher.excludes("a/b"), pattern)
    }
  }

  @Test
  fun `a regex metacharacter in a pattern means itself`() {
    // The format holds no metacharacter of a regex, and thus the regex tier escapes each of them. A gap in the escape either takes the
    // regex apart, which logs an error and fails this test, or changes what the pattern matches.
    val matcher = matcherOf("a?.b\$c^d(e)f{g}h|i+j]k*")

    assertTrue(matcher.excludes("aX.b\$c^d(e)f{g}h|i+j]k"))
    assertTrue(matcher.excludes("aX.b\$c^d(e)f{g}h|i+j]k-tail"))
    // The '.' means a dot and not any character.
    assertFalse(matcher.excludes("aXZb\$c^d(e)f{g}h|i+j]k"))
  }

  @Test
  fun `a case-insensitive file system tells no case apart at any tier`() {
    assertTrue(matcherOf("build", caseSensitive = false).excludes("Build"))
    assertTrue(matcherOf("*.log", caseSensitive = false).excludes("a.LOG", isDirectory = false))
    assertTrue(matcherOf("pre*", caseSensitive = false).excludes("PREfix"))
    assertTrue(matcherOf("*mid*", caseSensitive = false).excludes("aMIDb"))
    assertTrue(matcherOf("a?c", caseSensitive = false).excludes("ABC"))
    assertTrue(matcherOf("docs/generated/", caseSensitive = false).excludes("docs/Generated"))
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------------------------------------------------------------------

  /** The matcher of the index for [lines], as the reader and the index build it: the reader validates first, and the index compiles. */
  private fun matcherOf(vararg lines: String, caseSensitive: Boolean = true): AnalysisIgnoreMatcher =
    AnalysisIgnoreMatcher("file:///base", null, AnalysisIgnorePattern.compileAll(storedSourcesOf(*lines), caseSensitive), caseSensitive)

  /**
   * Answers as the index does: a path is out when it or a directory above it matches.
   * [com.intellij.workspaceModel.core.fileIndex.impl.ExcludedFileSet.ByUnscopedCondition] walks the parents of a file the same way.
   */
  private fun AnalysisIgnoreMatcher.excludes(relativePath: String, isDirectory: Boolean = true): Boolean {
    val segments = relativePath.split('/')
    return segments.indices.any { index ->
      // Every directory above the path itself is a directory, whatever the path itself is.
      isExcluded(segments.subList(0, index + 1).joinToString("/"), isDirectory = isDirectory || index < segments.lastIndex)
    }
  }

  private fun AnalysisIgnoreMatcher.isExcluded(relativePath: String, isDirectory: Boolean): Boolean =
    isExcluded(relativePath, relativePath.substringAfterLast('/'), isDirectory)

  /** The patterns that [lines] hold, as the parser takes them. */
  private fun sourcesOf(vararg lines: String): List<String> = lines.mapNotNull { AnalysisIgnorePattern.patternSource(it) }

  /** The patterns that [text] holds and the line number of each, as `AnalysisIgnoreFileReader` counts them. */
  private fun numberedSourcesOf(text: String): List<Pair<Int, String>> {
    var lineNumber = 0
    return text.lineSequence().mapNotNull { line ->
      lineNumber++
      AnalysisIgnorePattern.patternSource(line)?.let { lineNumber to it }
    }.toList()
  }

  /** The patterns of [lines] that the reader stores: a line that the format leaves out drops out. */
  private fun storedSourcesOf(vararg lines: String): List<String> =
    sourcesOf(*lines).filter { AnalysisIgnorePattern.validate(it) == AnalysisIgnoreValidated.Supported }

  /** The reason that the format refuses [line], or `null` when it holds it. */
  private fun reasonOf(line: String): AnalysisIgnoreUnsupported? =
    AnalysisIgnorePattern.patternSource(line)
      ?.let { AnalysisIgnorePattern.validate(it) as? AnalysisIgnoreValidated.Unsupported }
      ?.reason
}
