// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class LightJava10HighlightingTest : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = JAVA_10

  fun testRequiresJavaBase() {
    myFixture.configureByText("module-info.java", """
      module M {
        requires <error descr="Modifier 'static' not allowed here">static</error> <error descr="Modifier 'transitive' not allowed here">transitive</error> java.base;
      }""".trimIndent())
    myFixture.checkHighlighting()
  }
}