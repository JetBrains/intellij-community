/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    PsiClass arrayClass3 = myFactory.getArrayClass(LanguageLevel.JDK_1_4);
    PsiClass arrayClass5 = myFactory.getArrayClass(LanguageLevel.HIGHEST);
    assertEquals(LanguageLevel.JDK_1_3, PsiUtil.getLanguageLevel(arrayClass3));
    assertEquals(LanguageLevel.JDK_1_5, PsiUtil.getLanguageLevel(arrayClass5));
  }
}