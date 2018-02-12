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
package com.intellij.java.ide.util;

import com.intellij.JavaTestUtil;
import com.intellij.ide.util.JavaAnonymousClassesHelper;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author Konstantin Bulenkov
 */
public class JavaAnonymousClassesHelperTest extends LightCodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.configureByFile(getTestName(false) + ".java");
  }

  public void testSimple() {doTest(7);}
  public void testSimpleInConstructor() {doTest(2);}
  public void testInsideAnonymousMethod() {doTest(1);}
  public void testAnonymousParameterInAnonymousConstructor() {doTest(1);}
  public void testAnonymousParameterInAnonymousConstructor2() {doTest(2);}

  @SuppressWarnings("ConstantConditions")
  private void doTest(int num) {
    final PsiElement element = PsiUtilBase.getElementAtCaret(myFixture.getEditor()).getParent().getParent();

    assert element instanceof PsiAnonymousClass : "There should be anonymous class at caret but " + element + " found";

    assertEquals("$" + num, JavaAnonymousClassesHelper.getName((PsiAnonymousClass)element));
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/anonymous/";
  }
}
