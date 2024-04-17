// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc

import com.intellij.codeInsight.javadoc.JavaDocExternalFilter
import com.intellij.testFramework.LightJavaCodeInsightTestCase
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future

class JavaExternalFilterTest: LightJavaCodeInsightTestCase() {

  @OptIn(DelicateCoroutinesApi::class)
  fun testConvertReference() {
    val project = getProject()
    val psiFile = createFile("Test.java", "")

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