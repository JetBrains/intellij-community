// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc

import com.intellij.JavaTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection


class JavadocDeclarationInspectionTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath(): String {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/javaDoc/"
  }

  fun testJavaDocDeclaration() {
    doTest()
  }

  private fun doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.enableInspections(JavadocDeclarationInspection())
    myFixture.testHighlighting(true, false, false)
  }
}
