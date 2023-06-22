// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;

public class AddDefaultConstructorFixTest extends LightJavaCodeInsightFixtureTestCase {
  public void test_adding_constructor_to_super_class() {
    PsiClass superClass = myFixture.addClass("abstract class Foo { Foo(int a) {} }");
    myFixture.configureByText("a.java", "class Bar extends Foo { <caret>Bar() { } }");
    boolean madeWritable =
      CodeInsightTestFixtureImpl.withReadOnlyFile(superClass.getContainingFile().getVirtualFile(), getProject(),
                                                  () -> myFixture.launchAction(
                                                    myFixture.findSingleIntention("Add package-private no-args constructor")));
    assertEquals(2, superClass.getConstructors().length);
    assertTrue(madeWritable);
  }
}
