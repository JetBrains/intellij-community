// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Assert

class AnnotationHintsTest: LightJavaCodeInsightFixtureTestCase() {

  fun `test contract inferred annotation`() {
    val text = """
class Demo {
  private static int pure(int x, int y) {
    return x * y + 10;
  }
}"""
    myFixture.configureByText("A.java", text)
    myFixture.doHighlighting()
    // until proper infrastructure to test hints appeared
    val inlays = myFixture.editor.inlayModel.getBlockElementsInRange(0, myFixture.file.textRange.endOffset)
    Assert.assertEquals(1, inlays.size)
    assertEquals("[[@ Contract [( [[pure  =  true]] )]]]", (inlays.first().renderer as PresentationRenderer).presentation.toString())
  }
}