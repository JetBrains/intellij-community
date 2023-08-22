// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.generation.surroundWith

import com.intellij.codeInsight.generation.surroundWith.*
import com.intellij.lang.LanguageSurrounders
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.testFramework.LightJavaCodeInsightTestCase
import com.intellij.util.containers.ContainerUtil

class JavaSurroundWith13Test : LightJavaCodeInsightTestCase() {
  private val BASE_PATH = "/codeInsight/generation/surroundWith/java13/"
  
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

  private fun doTest(surrounder: Surrounder) {
    val fileName = getTestName(false)
    configureByFile("${BASE_PATH}${fileName}.java")

    val descriptor = ContainerUtil.getFirstItem(LanguageSurrounders.INSTANCE.allForLanguage(JavaLanguage.INSTANCE))!!
    val selectionModel = getEditor().selectionModel
    val elements = descriptor.getElementsToSurround(getFile(), selectionModel.selectionStart, selectionModel.selectionEnd)
    assertTrue(surrounder.isApplicable(elements))

    SurroundWithHandler.invoke(getProject(), getEditor(), getFile(), surrounder)

    checkResultByFile("${BASE_PATH}${fileName}_after.java")
  }
}