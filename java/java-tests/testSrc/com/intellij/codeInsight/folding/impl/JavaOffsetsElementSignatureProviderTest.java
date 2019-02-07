// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.folding.impl;

import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class JavaOffsetsElementSignatureProviderTest extends LightCodeInsightFixtureTestCase {
  private final OffsetsElementSignatureProvider myProvider = new OffsetsElementSignatureProvider();

  public void testJavaStringLiteral() {
    String text =
      "class Test {\n" +
      "    void test() {\n" +
      "        bundle.getMessage(\"this.is.my.key\");\n" +
      "    }\n" +
      "}";
    myFixture.configureByText("test.java", text);

    int startOffset = text.indexOf('"');
    int endOffset = text.indexOf(')', startOffset);


    String baseSignature = String.format("e#%d#%d", startOffset, endOffset);
    PsiElement implicitTop = myProvider.restoreBySignature(myFixture.getFile(), baseSignature, null);
    assertNotNull(implicitTop);

    PsiElement top = myProvider.restoreBySignature(myFixture.getFile(), baseSignature + "#0", null);
    assertSame(implicitTop, top);

    PsiElement bottom = myProvider.restoreBySignature(myFixture.getFile(), baseSignature + "#1", null);
    assertNotNull(bottom);
    assertNotSame(top, bottom);
  }
}
