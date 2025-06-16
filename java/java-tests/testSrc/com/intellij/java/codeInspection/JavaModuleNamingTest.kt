// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.codeInspection.java19modules.JavaModuleNamingInspection
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase

class JavaModuleNamingTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(JavaModuleNamingInspection())
  }

  fun testSimple() = highlighting("""module <warning descr="Module name 'foo1' should not use terminal digits to encode version information">foo1</warning>.bar { }""")
  fun testSuppress() = highlighting("""@SuppressWarnings("module") module foo1.bar { }""")
  fun testDevanagariDigitsAreBannedToo() = highlighting("""module <warning descr="Module name 'foo१' should not use terminal digits to encode version information">foo१</warning>.bar { }""")
  fun testMiddleDigitsAllowed() = highlighting("""module f0o.b4r { }""")

  fun testFix() {
    myFixture.configureByText("module-info.java", "module <caret>f0o123.b4r१ { }")
    myFixture.launchAction(myFixture.findSingleIntention("Rename"))
    myFixture.checkResult("module-info.java", "module f0o.b4r { }", false)
  }

  private fun highlighting(text: String) {
    myFixture.configureByText("module-info.java", text)
    myFixture.checkHighlighting()
  }
}