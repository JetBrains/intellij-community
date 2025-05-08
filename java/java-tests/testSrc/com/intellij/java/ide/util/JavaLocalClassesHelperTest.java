// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.util;

import com.intellij.JavaTestUtil;
import com.intellij.ide.util.JavaLocalClassesHelper;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ObjectUtils;

public class JavaLocalClassesHelperTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.configureByFile(getTestName(false) + ".java");
  }

  public void testSimple() {
    doTest("$1Local");
  }

  public void testMultipleNames() {
    doTest("$1B");
  }

  public void testSameName() {
    doTest("$2Local");
  }

  private void doTest(String expectedName) {
    final PsiElement element = PsiUtilBase.getElementAtCaret(myFixture.getEditor()).getParent().getParent();

    PsiDeclarationStatement declaration = ObjectUtils.tryCast(element, PsiDeclarationStatement.class);
    PsiClass aClass = declaration == null ? null : ObjectUtils.tryCast(declaration.getDeclaredElements()[0], PsiClass.class);
    assert aClass != null && PsiUtil.isLocalClass(aClass) : "There should be local class at caret but " + element + " found";

    assertEquals(expectedName, JavaLocalClassesHelper.getName(aClass));
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/local/";
  }
}