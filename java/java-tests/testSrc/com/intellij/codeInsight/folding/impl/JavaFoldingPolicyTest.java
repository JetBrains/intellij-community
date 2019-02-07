// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.folding.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;

public class JavaFoldingPolicyTest extends AbstractFoldingPolicyTest {
  public void testAdditionalChildDocComments() {
    myFixture.configureByText("Test.java",
                              "/** outer **/\n" +
                              "class Test {\n" +
                              "/** <caret>inner **/\n" +
                              "}");
    PsiElement element = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()),
                                                     PsiDocComment.class, false);
    assertNotNull(element);
    String signature = FoldingPolicy.getSignature(element);
    if (signature != null) {
      assertEquals(element, FoldingPolicy.restoreBySignature(element.getContainingFile(), signature));
    }
  }
}
