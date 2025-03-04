// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import one.util.streamex.StreamEx;

import java.util.Map;
import java.util.function.Function;

public final class PsiNewExpressionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testArrayAnnotations() {
    myFixture.configureByText("Foo.java", """
      @interface A { }
      @interface B { }
      @interface C { }
      @interface D { }
      class Foo {
        int[][] data = new @A <caret>int @B @C [] @D [] {};
      }
      """);
    PsiFile file = myFixture.getFile();
    PsiNewExpression newExpression =
      PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiNewExpression.class);
    assertNotNull(newExpression);
    assertEquals("@A int @B @C [] @D []", newExpression.getType().getCanonicalText(true));
    Map<String, PsiAnnotation> allAnnotations =
      StreamEx.of(PsiTreeUtil.collectElementsOfType(newExpression, PsiAnnotation.class))
        .toMap(anno -> anno.getQualifiedName(), Function.identity());
    assertEquals(4, allAnnotations.size());
    

    Map<String, String> expectedOwnerTexts = Map.of(
      "A", "@A int",
      "B", "@A int @B @C [] @D []",
      "C", "@A int @B @C [] @D []",
      "D", "@A int @D []"
    );

    for (Map.Entry<String, String> entry : expectedOwnerTexts.entrySet()) {
      String anno = entry.getKey();
      String expectedType = entry.getValue();
      PsiAnnotationOwner owner = allAnnotations.get(anno).getOwner();
      PsiType type = assertInstanceOf(owner, PsiType.class);
      assertEquals(anno, expectedType, type.getCanonicalText(true));
    }
  }
}
