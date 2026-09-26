// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public final class PsiClassUtilTest extends LightJavaCodeInsightFixtureTestCase {
  public void testIsThrowable() {
    PsiFile file = myFixture.configureByText("Test.java", """
      class Test extends RuntimeException {}
      class Test2 extends java.util.ArrayList<String> {}
      // Return true only for well-formed inheritors
      class Test3 implements RuntimeException {}
      class Test4 extends String, RuntimeException {}
      class Test5<T> extends RuntimeException {}
      """);
    PsiClass[] classes = ((PsiJavaFile)file).getClasses();
    assertTrue(PsiClassUtil.isThrowable(classes[0]));
    assertFalse(PsiClassUtil.isThrowable(classes[1]));
    assertFalse(PsiClassUtil.isThrowable(classes[2]));
    assertFalse(PsiClassUtil.isThrowable(classes[3]));
    assertFalse(PsiClassUtil.isThrowable(classes[4]));
  }

  public void testIsThrowableNoRecursion() {
    PsiFile file = myFixture.configureByText("Test.java",
                                             """
                                               class Test extends Test {}
                                               class Test1 extends Test2 {}
                                               class Test2 extends Test1 {}
                                               """);
    PsiClass[] classes = ((PsiJavaFile)file).getClasses();
    assertFalse(PsiClassUtil.isThrowable(classes[0]));
    assertFalse(PsiClassUtil.isThrowable(classes[1]));
    assertFalse(PsiClassUtil.isThrowable(classes[2]));
  }
}
