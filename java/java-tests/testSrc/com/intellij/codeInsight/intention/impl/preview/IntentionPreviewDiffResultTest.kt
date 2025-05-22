// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewDiffResult.Companion.fromCustomDiff
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo.CustomDiff
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Test

class IntentionPreviewDiffResultTest : LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun testSingleWord() {
    val diffs = createDiffs("""
        public class Test {}
        """, """
        private class Test {}
        """)
    assertEquals("1 : #!private!# class Test {}", diffs)
  }

  @Test
  fun testDeleteParens() {
    val diffs = createDiffs("""
        println((x+y))
        """, """
        println(x+y)
        """)
    assertEquals("1 : println(#-(-#x+y)#-)-#", diffs)
  }

  @Test
  fun testDeleteAndChange() {
    val diffs = createDiffs("""
        println((xxx+yyy))
        """, """
        println(xxx-yyy)
        """)
    assertEquals("1 : println(#!xxx-yyy)!#", diffs)
  }

  @Test
  fun testAddParens() {
    val diffs = createDiffs("""
        println(x+y)
        """, """
        println((x+y))
        """)
    assertEquals("1 : println(#+(+#x+y)#+)+#", diffs)
  }

  @Test
  fun testReverseLoop() {
    val diffs = createDiffs("""
        for (int i=0; i<10; i++) {
        }
        """, """
        for (int i=9; i>=0; i--) {
        }
        """)
    assertEquals("1 : for (int i=#!9!#; i#!>=0!#; i#!--!#) {", diffs)
  }

  @Test
  fun testDeleteAll() {
    val diffs = createDiffs("""
        public class Test {}
        """, "")
    assertEquals("1 : #-public class Test {}-#", diffs)
  }

  @Test
  fun testDeleteMultiline() {
    val diffs = createDiffs("""
        public class Test {
          void test() {}
        }
        """, "")
    assertEquals("""
        1 : #-public class Test {
        2 :   void test() {}
        3 : }-#""".trimIndent(), diffs)
  }

  @Test
  fun testAddMultiline() {
    val diffs = createDiffs("", """
        public class Test {
          void test() {}
        }
        """)
    assertEquals("""
      1 : public class Test {
      2 :   void test() {}
      3 : }""".trimIndent(), diffs)
  }

  @Test
  fun testChangesSeparatedByOneString() {
    val diffs = createDiffs("""
        var x = 2;
        println(1);
        println(x);""", """
        var y = 2;
        println(1);
        println(y);""")
    assertEquals("""
      1 : var y = 2;
      2 : println(1);
      3 : println(y);""".trimIndent(), diffs)
  }

  @Test
  fun testChangesSeparatedByTwoStrings() {
    val diffs = createDiffs("""
        var x = 2;
        println(0);
        println(1);
        println(x);""", """
        var y = 2;
        println(0);
        println(1);
        println(y);""", "Test.java")
    assertEquals("""
      // Test.java
      ---------------------
      1 : var #!y!# = 2;
      ---------------------
      4 : println(#!y!#);""".trimIndent(), diffs)
  }
  
  @Test
  fun testResultIsSubstring() {
    val diffs = createDiffs("""
      non changed
      prefix
      preserved
      suffix
    """.trimIndent(), """
      non changed
      preserved
    """.trimIndent(), "Test.xml")
    assertEquals("""
      <!-- Test.xml -->
      ---------------------
      1 : #-prefix-#
      2 : preserved
      3 : #-suffix-#
    """.trimIndent(), diffs)
  }

  @Test
  fun testRemoveEscapes() {
    val diffs = createDiffs("""
        String s = "\'Scare\' quotes";""", """
        String s = "'Scare' quotes";""")
    assertEquals("""1 : String s = "#-\-#'Scare#-\-#' quotes";""", diffs)
  }

  @Test
  fun testAddSpaceInTheMiddle() {
    val diffs = createDiffs("class Test{}", "class Test {}")
    assertEquals("0 : class Test#+ +#{}", diffs)
  }
  
  @Test
  fun testAddSpaceAtTheEnd() {
    val diffs = createDiffs("class Test{}", "class Test{} ")
    assertEquals("0 : class Test{}#+ +#", diffs)
  }
  
  @Test
  fun testLotsOfLines() {
    val diffTo109 = """
      // MyFile.java
      ---------------------
      0 : line #!I!#
      ---------------------
      9 : line I0
      10: line II
      11: line I2
      12: line I3
      13: line I4
      14: line I5
      15: line I6
      16: line I7
      17: line I8
      18: line I9
      19: line 20
      20: line 2I
      ---------------------
      30: line #!3I!#
      ---------------------
      40: line #!4I!#
      ---------------------
      50: line #!5I!#
      ---------------------
      60: line #!6I!#
      ---------------------
      70: line #!7I!#
      ---------------------
      80: line #!8I!#
      ---------------------
      90: line #!9I!#
      ---------------------
      99: line I00
      100: line I0I
      101: line I02
      102: line I03
      103: line I04
      104: line I05
      105: line I06
      106: line I07
      107: line I08
      108: line I09
      109: line II0
    """.trimIndent()
    val diffTo109AndEllipsis = "$diffTo109\n---------------------\n..."
    val oldText1 = (1..110).joinToString("\n") { "line $it" } + "\nline 222\nline 223"
    assertEquals(diffTo109, createDiffs(oldText1, oldText1.replace("1", "I"), "MyFile.java"))
    val oldText2 = (1..110).joinToString("\n") { "line $it" } + "\nline 222\nline 223\nline 331"
    assertEquals(diffTo109AndEllipsis, createDiffs(oldText2, oldText2.replace("1", "I"), "MyFile.java"))
    val oldText3 = (1..200).joinToString("\n") { "line $it" }
    assertEquals(diffTo109AndEllipsis, createDiffs(oldText3, oldText3.replace("1", "I"), "MyFile.java"))
  }
  
  @Test
  fun testLotsOfLinesWithFragments() {
    val oldText = (1..200).joinToString("\n") { "line $it" }
    val newText = (20..180).joinToString("\n") { "line $it" }
    assertEquals("""
      0 : #-line 1
      1 : line 2
      2 : line 3
      3 : line 4
      4 : line 5
      5 : line 6
      6 : line 7
      7 : line 8
      8 : line 9
      9 : line 10
      10: line 11
      11: line 12
      12: line 13
      13: line 14
      14: line 15
      15: line 16
      16: line 17
      17: line 18
      18: line 19-#
      ---------------------
      180: #-line 181
      181: line 182
      182: line 183
      183: line 184
      184: line 185
      185: line 186
      186: line 187
      187: line 188
      188: line 189
      189: line 190
      190: line 191
      191: line 192
      192: line 193-#
      ---------------------
      ...
    """.trimIndent(), createDiffs(oldText, newText))
  }
  
  @Test
  fun testMultiDiff() {
    val multiFileDiff = IntentionPreviewInfo.MultiFileDiff(listOf(
      CustomDiff(JavaFileType.INSTANCE, "Test1.java", "class A {}", "class B {}"),
      CustomDiff(JavaFileType.INSTANCE, "Test2.java", "class C {}", "class D {}"),
    ))
    val result = IntentionPreviewDiffResult.fromMultiDiff(multiFileDiff).formatResult()
    assertEquals("""
      // Test1.java
      ---------------------
      class #!B!# {}
      ---------------------
      // Test2.java
      ---------------------
      class #!D!# {}
    """.trimIndent(), result)
  }
  
  @Test
  fun testNoLineNumbersIgnoreWhiteSpace() {
    val result = IntentionPreviewDiffResult.create(
      JavaFileType.INSTANCE, "class A {}", "class A {} ", ComparisonPolicy.IGNORE_WHITESPACES, false, "Test.java"
    ).formatResult()
    assertEquals("", result)
  }
  
  /**
   * Returns a textual representation of diffs from created and modified text, how they will look
   * in the intention preview window. Each diff chunk is separated via a horizontal dashed line.
   * Line numbers with colons are prepended (the same line numbers which will be displayed to the user).
   * The highlighted parts of code are rendered using a special syntax: #+highlighted as added+#,
   * #!highlighted as updated!#, and #-highlighted as deleted-#.
   * 
   * @param origText original file text
   * @param modifiedText file text after the changes are applied
   * @param fileName (optional) name of the file to be used; it creates a separate chunk with a comment
   */
  private fun createDiffs(origText: String, modifiedText: String, fileName: String? = null): String {
    val fileType = if (fileName == null) JavaFileType.INSTANCE else FileTypeManager.getInstance().getFileTypeByFileName(fileName)
    return fromCustomDiff(CustomDiff(fileType, fileName, origText, modifiedText, true)).shorten(32).formatResult()
  }

  private fun IntentionPreviewDiffResult.formatResult(): String {
    return diffs.joinToString(
      separator = "\n---------------------\n") { diffInfo ->
      val addends = diffInfo.fragments.flatMap { fragment ->
        listOf(fragment.start to when (fragment.type) {
          IntentionPreviewDiffResult.HighlightingType.ADDED -> "#+"
          IntentionPreviewDiffResult.HighlightingType.UPDATED -> "#!"
          IntentionPreviewDiffResult.HighlightingType.DELETED -> "#-"
        }, fragment.end to when (fragment.type) {
          IntentionPreviewDiffResult.HighlightingType.ADDED -> "+#"
          IntentionPreviewDiffResult.HighlightingType.UPDATED -> "!#"
          IntentionPreviewDiffResult.HighlightingType.DELETED -> "-#"
        })
      }.toMap()
      val text = diffInfo.fileText
      val withHighlighters = buildString {
        for (i: Int in 0..text.length) {
          append(addends[i] ?: "")
          if (i < text.length) append(text[i])
        }
      }
      withHighlighters.split("\n").mapIndexed { index, s ->
        if (diffInfo.startLine == -1) s else String.format("%-2d: ", diffInfo.startLine + index) + s
      }.joinToString(separator = "\n")
    }
  }
}
