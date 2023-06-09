// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.AnnotationInlayProvider
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import org.intellij.lang.annotations.Language

class AnnotationHintsTest : InlayHintsProviderTestCase() {

  fun `test contract inferred annotation`() {
    val text = """
class Demo {
/*<# block [[@ Contract [( [[pure  =  true]] )]]] #>*/
  private static int pure(int x, int y) {
    return x * y + 10;
  }
}"""
    testAnnotations(text)
  }

  fun `test contract nullable`() {
    val text = """
public class E {
/*<# block [[@ Contract [( ["null -> true"] )]]] #>*/
  static boolean foo(E e) {
    if (e != null) {
      e.foo(new E());
    } else {
      return true;
    }
  }
}"""
    testAnnotations(text)
  }

  fun `test no parameters have no parens`() {
    val text = """
public class E {
/*<# block [[@ Contract [( [[pure  =  true]] )]] [@ Nullable]] #>*/
  static Boolean foo(E e) {
    if (true) return false;
    return null;
  }
}"""
    testAnnotations(text)
  }

  fun `test parameters annotations on the same line`() {
    val text = """
public class E {
  void foo(
      /*<# [[@ NotNull]] #>*/String s
    ) {
    s.length();  
  }
}"""
    testAnnotations(text)
  }

  private fun testAnnotations(
    @Language("JAVA") text: String,
    settings: AnnotationInlayProvider.Settings = AnnotationInlayProvider.Settings(showInferred = true, showExternal = true)
  ) {
    doTestProvider(
      "test.java",
      text,
      AnnotationInlayProvider(),
      settings
    )
  }
}