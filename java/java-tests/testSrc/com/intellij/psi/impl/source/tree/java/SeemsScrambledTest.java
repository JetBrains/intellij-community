// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class SeemsScrambledTest extends LightJavaCodeInsightFixtureTestCase {
  public void test_Id_annotation() {
    assertFalse(PsiReferenceExpressionImpl.seemsScrambledByStructure(myFixture.addClass("public @interface Id {}")));
  }

  public void test_inner_enum() {
    assertFalse(PsiReferenceExpressionImpl.seemsScrambledByStructure(
      myFixture.addClass("public class Foo { enum v1 {} }").getInnerClasses()[0]));
  }

  public void test_scrambled() {
    assertTrue(PsiReferenceExpressionImpl.seemsScrambledByStructure(myFixture.addClass("public class a { void b() {} }")));
  }

  public void test_has_non_scrambled_method() {
    assertFalse(PsiReferenceExpressionImpl.seemsScrambledByStructure(myFixture.addClass("public class a { void doSomething() {} }")));
  }
}
