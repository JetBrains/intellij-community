// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.ImplicitTypeInlayProvider
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase

class ImplicitTypeInlayProviderTest : InlayHintsProviderTestCase() {

  fun `test implicit int not shown`() {
    val text = """
class Demo {
  private static void main() {
    var x = 12;
  }
}"""
    testAnnotations(text)
  }

  fun `test call result`() {
    val text = """
class Demo {
  static String foo() {}
  private static void pure(int x, int y) {
    var x<# [:  String] #> = foo();
  }
}"""
    testAnnotations(text)
  }


  private fun testAnnotations(
    text: String
  ) {
    testProvider(
      "test.java",
      text,
      ImplicitTypeInlayProvider(),
      NoSettings()
    )
  }
}