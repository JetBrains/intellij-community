// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.search

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture.Companion.heavyJavaCodeInsightFixture
import com.intellij.openapi.application.readAction
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.Integer.max

@TestApplication
class FindUsagesJava14Test {
  private val fixture by heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath())

  @BeforeEach
  fun setUp() {
    IdeaTestUtil.setProjectLanguageLevel(fixture.project, LanguageLevel.HIGHEST)
    val root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/findUsages14/" + fixture.getTestName(true)
    PsiTestUtil.removeAllRoots(fixture.module, IdeaTestUtil.getMockJdk17())
    fixture.createTestProjectStructure(root)
  }

  @Test
  fun testRecordComponentMethod(): Unit = timeoutRunBlocking {
    readAction {
      val record = fixture.javaFacade.findClass("pack.MyRecord", GlobalSearchScope.moduleScope(fixture.module))!!
      assertTrue(record.isRecord)
      testReferences(record.recordComponents[0], """
        5     String s = s();
                         ^
        
        12     asd.s();
               ^^^^^
      """.trimIndent())
    }
  }

  @Test
  fun testRecordComponentField(): Unit = timeoutRunBlocking {
    readAction {
      val record = fixture.javaFacade.findClass("pack.MyRecord", GlobalSearchScope.moduleScope(fixture.module))!!
      assertTrue(record.isRecord)
      testReferences(record.recordComponents[0], """
        5     s.substring(10, 12);
              ^
      """.trimIndent())
    }
  }

  @Test
  fun testCompactConstructor(): Unit = timeoutRunBlocking {
    readAction {
      val record = fixture.javaFacade.findClass("pack.MyRecord", GlobalSearchScope.moduleScope(fixture.module))!!
      assertTrue(record.isRecord)
      testReferences(record.recordComponents[0], """
        5     String x = s();
                         ^
      """.trimIndent())
    }
  }

  @Test
  fun testSeparateFiles(): Unit = timeoutRunBlocking {
    readAction {
      val record = fixture.javaFacade.findClass("pack1.MyRecord", GlobalSearchScope.moduleScope(fixture.module))!!
      assertTrue(record.isRecord)
      testReferences(record.recordComponents[0], """
5     String s = s();
                 ^

8     asd.s();
      ^^^^^
      """.trimIndent())
    }
  }

  private fun testReferences(elementToSearch: PsiElement, expectedText: String): Array<out PsiElement> {
    val elements = ReferencesSearch.search(elementToSearch, GlobalSearchScope.moduleScope(fixture.module), false)
      .mapping { reference: PsiReference -> reference.element }
      .toArray(PsiElement.EMPTY_ARRAY)
    val actualText = elements.sortedBy { it.textRange.startOffset }.joinToString("\n\n") { createSnippet(it) }
    assertEquals(expectedText, actualText)
    return elements
  }

  private fun createSnippet(element: PsiElement) : String {
    val document = fixture.getDocument(element.containingFile)!!
    val textRange = element.textRange
    val startLine = document.getLineNumber(textRange.startOffset)
    val lastLine = document.getLineNumber(textRange.endOffset)
    val firstLineStartOffset = document.getLineStartOffset(startLine)
    val lastLineEndOffset = document.getLineEndOffset(lastLine)
    var lineNumber = startLine
    val snippetWithLineNumbers = document.text.subSequence(firstLineStartOffset, lastLineEndOffset)
      .split("\n").joinToString {
        val oldLineNumber = lineNumber
        lineNumber++
        "${oldLineNumber + 1} $it"
      }
    val lastLineStartOffset = document.getLineStartOffset(lastLine)
    val highlightLength = textRange.endOffset - max(lastLineStartOffset, textRange.startOffset)
    val spaceCount = textRange.endOffset - lastLineStartOffset - highlightLength
    return buildString {
      append(snippetWithLineNumbers)
      append("\n")
      repeat(spaceCount + (lastLine + 1).toString().length + 1) {
        append(" ")
      }
      repeat(highlightLength) {
        append("^")
      }
    }
  }
}