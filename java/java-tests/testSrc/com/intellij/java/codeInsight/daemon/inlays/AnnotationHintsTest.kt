// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

class AnnotationHintsTest: LightCodeInsightFixtureTestCase() {
  fun check(@Language("Java") text: String) {
    myFixture.configureByText("A.java", text)
    myFixture.testInlays({ (it.renderer as PresentationRenderer).presentation.toString() }, { it.renderer is PresentationRenderer })
  }

  fun `test contract inferred annotation`() {
    check("""
class Demo {
  <hint text="[[@ Contract [( [[pure  =  true]] )]]]"/>private static int pure(int x, int y) {
    return x * y + 10;
  }
}""")
  }
}