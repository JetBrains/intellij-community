// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.generation.surroundWith

import com.intellij.codeInsight.generation.surroundWith.*
import com.intellij.java.codeInsight.folding.JavaFoldingTestCase
import com.intellij.lang.LanguageSurrounders
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightCodeInsightTestCase
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.containers.ContainerUtil

class JavaSurroundWith12Test : LightCodeInsightTestCase() {

  private val BASE_PATH = "/codeInsight/generation/surroundWith/java12/"

  override fun getProjectDescriptor(): LightProjectDescriptor = JavaFoldingTestCase.JAVA_12

  fun testCaseBlockWithIf() = doTest(JavaWithIfSurrounder())
  fun testCaseResultWithIf() = doTest(JavaWithIfSurrounder())
  fun testCaseThrowWithIf() = doTest(JavaWithIfSurrounder())
  fun testCaseResultWithSynchronized() = doTest(JavaWithSynchronizedSurrounder())
  fun testDefaultBlockWithTryFinally() = doTest(JavaWithTryFinallySurrounder())
  fun testCaseThrowWithTryCatch() = doTest(JavaWithTryCatchSurrounder())
  fun testDefaultResultWithTryCatchFinally() = doTest(JavaWithTryCatchFinallySurrounder())
  fun testDefaultBlockWithDoWhile() = doTest(JavaWithDoWhileSurrounder())
  fun testCaseThrowWithBlock() = doTest(JavaWithBlockSurrounder())
  fun testDefaultResultWithRunnable() = doTest(JavaWithRunnableSurrounder())
  fun testCatchBlockWithFor() = doTest(JavaWithForSurrounder())
  fun testCatchResultWithFor() = doTest(JavaWithForSurrounder())
  fun testDefaultThrowWithIfElse() = doTest(JavaWithIfElseSurrounder())
  fun testDefaultResultWithWhile() = doTest(JavaWithWhileSurrounder())

  private fun doTest(surrounder: Surrounder) = doTest(getTestName(false), surrounder)

  private fun doTest(fileName: String, surrounder: Surrounder) {
    configureByFile("$BASE_PATH$fileName.java")

    val item = ContainerUtil.getFirstItem(LanguageSurrounders.INSTANCE.allForLanguage(JavaLanguage.INSTANCE))!!
    val selectionModel = getEditor().selectionModel
    val elements = item.getElementsToSurround(getFile(), selectionModel.selectionStart, selectionModel.selectionEnd)
    assertTrue(surrounder.isApplicable(elements))

    SurroundWithHandler.invoke(getProject(), getEditor(), getFile(), surrounder)

    checkResultByFile(BASE_PATH + fileName + "_after.java")
  }
}
