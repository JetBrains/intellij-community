// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.impl;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;

import static com.intellij.testFramework.LightCodeInsightTestCase.getJavaFacade;

public class JavaElementFactoryTest extends LightCodeInsightFixtureTestCase {
  private PsiElementFactory myFactory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFactory = getJavaFacade().getElementFactory();
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
      assertEquals(level, PsiUtil.getLanguageLevel(myFactory.getArrayClass(level)));
    }
  }
}