// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class CurrentLineFixesTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testCursorPosition(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText("x.java", "class Foo implements Runnable {<caret>}")
    withContext(Dispatchers.EDT) {
      myFixture.launchAction("Make 'Foo' abstract")
      myFixture.checkResult("abstract class Foo implements Runnable {<caret>}")
    }
  }
}