// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class SeemsScrambledTest extends LightJavaCodeInsightFixtureTestCase {
  public void testIdAnnotation() {
    assertFalse(PsiReferenceExpressionImpl.seemsScrambledByStructure(myFixture.addClass("public @interface Id {}")));
  }

  public void testInnerEnum() {
    assertFalse(PsiReferenceExpressionImpl.seemsScrambledByStructure(
      myFixture.addClass("public class Foo { enum v1 {} }").getInnerClasses()[0]));
  }

  public void testScrambled() {
    assertTrue(PsiReferenceExpressionImpl.seemsScrambledByStructure(myFixture.addClass("public class a { void b() {} }")));
  }

  public void testHasNonScrambledMethod() {
    assertFalse(PsiReferenceExpressionImpl.seemsScrambledByStructure(myFixture.addClass("public class a { void doSomething() {} }")));
  }
}
