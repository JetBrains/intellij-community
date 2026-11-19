// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.codeInspection.java19modules.JavaModuleNamingInspection
import com.intellij.java.testFramework.fixtures.lightJava9ModulesFixture
import com.intellij.java.testFramework.fixtures.lightJava9ModulesProjectFixture
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JavaModuleNamingTest {
  companion object {
    private val projectFixture = lightJava9ModulesProjectFixture()
  }

  private val java9 by lightJava9ModulesFixture(projectFixture)
  private val myFixture get() = java9.fixture

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(JavaModuleNamingInspection())
  }

  @Test
  fun testSimple(): Unit = highlighting("""module <warning descr="Module name 'foo1' should not use terminal digits to encode version information">foo1</warning>.bar { }""")
  @Test
  fun testSuppress(): Unit = highlighting("""@SuppressWarnings("module") module foo1.bar { }""")
  @Test
  fun testDevanagariDigitsAreBannedToo(): Unit = highlighting("""module <warning descr="Module name 'foo१' should not use terminal digits to encode version information">foo१</warning>.bar { }""")
  @Test
  fun testMiddleDigitsAllowed(): Unit = highlighting("""module f0o.b4r { }""")

  @Test
  fun testFix(): Unit = timeoutRunBlocking {
    myFixture.configureByText("module-info.java", "module <caret>f0o123.b4r१ { }")
    withContext(Dispatchers.EDT) {
      myFixture.launchAction(myFixture.findSingleIntention("Rename"))
      myFixture.checkResult("module-info.java", "module f0o.b4r { }", false)
    }
  }

  private fun highlighting(text: String) {
    myFixture.configureByText("module-info.java", text)
    myFixture.checkHighlighting()
  }
}