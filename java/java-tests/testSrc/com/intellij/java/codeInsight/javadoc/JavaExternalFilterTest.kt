// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc

import com.intellij.codeInsight.javadoc.JavaDocExternalFilter
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.fixtures.lightJavaTestDescriptor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.LocalTimeCounter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class JavaExternalFilterTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor())
  }

  @OptIn(DelicateCoroutinesApi::class)
  @Test
  fun testConvertReference(): Unit = timeoutRunBlocking {
    val project = projectFixture.get().project
    val psiFile = readAction {
      PsiFileFactory.getInstance(project)
        .createFileFromText("Test.java", FileTypeManager.getInstance().getFileTypeByFileName("Test.java"), "", LocalTimeCounter.currentTime(),
                            true, false)
    }

    val filter = GlobalScope.future {
      val filter = JavaDocExternalFilter(project)
      filter.setElement(psiFile)
      return@future filter
    }.join()

    assertEquals(
      filter.correctRefs("https://jetbrains.com", """<a href="#foo">""").trim(),
      """<a href="psi_element://Test.java###foo">"""
    )
    assertEquals(
      filter.correctRefs("https://jetbrains.com", """<a href="/foo">""").trim(),
      """<a href="https://jetbrains.com/foo">"""
    )
    assertEquals(
      filter.correctRefs("https://jetbrains.com", """<a href="/foo#bar">""").trim(),
      """<a href="https://jetbrains.com/foo#bar">"""
    )
    assertEquals(
      filter.correctRefs("https://www.jetbrains.com", """<a href="//foo/bar">""").trim(),
      """<a href="https://foo/bar">"""
    )
    assertEquals(
      filter.correctRefs("https://jetbrains.com", """<a href="//foo/bar#baz">""").trim(),
      """<a href="https://foo/bar#baz">"""
    )
  }
}
