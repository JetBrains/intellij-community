// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewDiffResult.Companion.fromCustomDiff
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo.CustomDiff
import com.intellij.ide.highlighter.JavaFileType
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
  fun testRemoveEscapes() {
    val diffs = createDiffs("""
        String s = "\'Scare\' quotes";""", """
        String s = "'Scare' quotes";""")
    assertEquals("""1 : String s = "#-\-#'Scare#-\-#' quotes";""", diffs)
  }

  private fun createDiffs(origText: String, modifiedText: String, fileName: String? = null): String {
    return fromCustomDiff(CustomDiff(JavaFileType.INSTANCE, fileName, origText, modifiedText, true)).diffs.joinToString(
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
