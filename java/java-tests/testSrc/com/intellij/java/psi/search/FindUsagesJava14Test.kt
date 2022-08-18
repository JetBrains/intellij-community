// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.search

import com.intellij.JavaTestUtil
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.JavaPsiTestCase
import com.intellij.testFramework.PsiTestUtil
import junit.framework.TestCase
import java.lang.Integer.max

class FindUsagesJava14Test : JavaPsiTestCase() {
  override fun setUp() {
    super.setUp()
    LanguageLevelProjectExtension.getInstance(myJavaFacade.project).languageLevel = LanguageLevel.HIGHEST
    val root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/findUsages14/" + getTestName(true)
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17())
    createTestProjectStructure(root)
  }

  fun testRecordComponentMethod() {
    val record = myJavaFacade.findClass("pack.MyRecord", GlobalSearchScope.moduleScope(myModule))!!
    TestCase.assertTrue(record.isRecord)
    testReferences(record.recordComponents[0], """
      5     String s = s();
                       ^
      
      12     asd.s();
             ^^^^^
    """.trimIndent())
  }

  fun testRecordComponentField() {
    val record = myJavaFacade.findClass("pack.MyRecord", GlobalSearchScope.moduleScope(myModule))!!
    TestCase.assertTrue(record.isRecord)
    testReferences(record.recordComponents[0], """
      5     s.substring(10, 12);
            ^
    """.trimIndent())
  }

  fun testCompactConstructor() {
    val record = myJavaFacade.findClass("pack.MyRecord", GlobalSearchScope.moduleScope(myModule))!!
    TestCase.assertTrue(record.isRecord)
    testReferences(record.recordComponents[0], """
      5     String x = s();
                       ^
    """.trimIndent())
  }

  fun testSeparateFiles() {
    val record = myJavaFacade.findClass("pack1.MyRecord", GlobalSearchScope.moduleScope(myModule))!!
    TestCase.assertTrue(record.isRecord)
    testReferences(record.recordComponents[0], """
5     String s = s();
                 ^

8     asd.s();
      ^^^^^
    """.trimIndent())
  }

  private fun testReferences(elementToSearch: PsiElement, expectedText: String): Array<out PsiElement> {
    val elements = ReferencesSearch.search(elementToSearch, GlobalSearchScope.moduleScope(myModule), false)
      .mapping { reference: PsiReference -> reference.element }
      .toArray(PsiElement.EMPTY_ARRAY)
    val actualText = elements.sortedBy { it.textRange.startOffset }.joinToString("\n\n") { createSnippet(it) }
    assertEquals(expectedText, actualText)
    return elements
  }

  private fun createSnippet(element: PsiElement) : String {
    val document = getDocument(element.containingFile)
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