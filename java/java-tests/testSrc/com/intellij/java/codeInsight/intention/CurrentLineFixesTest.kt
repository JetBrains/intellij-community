// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class CurrentLineFixesTest: LightJavaCodeInsightFixtureTestCase() {
  fun testCursorPosition() {
    myFixture.configureByText("x.java", "class Foo implements Runnable {<caret>}")
    myFixture.launchAction("Make 'Foo' abstract")
    myFixture.checkResult("abstract class Foo implements Runnable {<caret>}")
  }
}