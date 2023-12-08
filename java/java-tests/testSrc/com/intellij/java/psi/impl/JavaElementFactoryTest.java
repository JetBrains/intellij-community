// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.impl;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;

public class JavaElementFactoryTest extends LightJavaCodeInsightFixtureTestCase {
  private PsiElementFactory myFactory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFactory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
  }

  @Override
  protected void tearDown() throws Exception {
    myFactory = null;
    super.tearDown();
  }

  public void testDocCommentTrimming() {
    myFactory.createDocCommentFromText(" /** ... */");
  }

  public void testDocCommentEmpty() {
    try {
      myFactory.createDocCommentFromText("");
      fail();
    }
    catch (IncorrectOperationException ignored) { }
  }

  public void testArrayClassLanguageLevel() {
    for (LanguageLevel level : LanguageLevel.values()) {
      assertEquals(level, PsiUtil.getDeclaredLanguageLevel(myFactory.getArrayClass(level)));
    }
  }
}