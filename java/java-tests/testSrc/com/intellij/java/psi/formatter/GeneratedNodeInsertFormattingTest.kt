// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Postponed formatting reformats an inserted element whose tree is marked as generated and only re-indents one that is not.
 */
@TestApplication
class GeneratedNodeInsertFormattingTest {
  private companion object {
    val project = lightJavaProjectFixture()
    const val SOURCE = "class B { void foo(){int a=1+2;} }"
  }

  private val fixture = lightJavaCodeInsightFixture(project)

  @Test
  fun `a method from a file created with markAsCopy is reformatted`() {
    val text = insertMethod { createFileFromText(markAsCopy = true) }
    assertTrue("int a = 1 + 2;" in text, text)
  }

  @Test
  fun `a method from a file created without markAsCopy keeps its whitespace`() {
    val text = insertMethod { createFileFromText(markAsCopy = false) }
    assertTrue("int a=1+2;" in text, text)
  }

  private fun createFileFromText(markAsCopy: Boolean): PsiJavaFile =
    PsiFileFactory.getInstance(fixture.get().project)
      .createFileFromText("B.java", JavaLanguage.INSTANCE, SOURCE, false, markAsCopy) as PsiJavaFile

  /**
   * Adds the method of the class in [source] to a class of a physical file and runs the postponed formatting.
   */
  private fun insertMethod(source: () -> PsiJavaFile): String {
    val codeInsight = fixture.get()
    val target = codeInsight.configureByText("A.java", "class A {\n}") as PsiJavaFile
    EdtTestUtil.runInEdtAndWait<Throwable> {
      WriteCommandAction.runWriteCommandAction(codeInsight.project) {
        target.classes[0].add(source().classes[0].methods[0])
        PostprocessReformattingAspect.getInstance(codeInsight.project).doPostponedFormatting()
      }
    }
    return runReadActionBlocking { target.text }
  }
}
