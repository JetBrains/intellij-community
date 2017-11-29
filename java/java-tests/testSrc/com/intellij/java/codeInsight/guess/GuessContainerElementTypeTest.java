
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
package com.intellij.java.codeInsight.guess;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class GuessContainerElementTypeTest extends LightCodeInsightTestCase {

  private static final String BASE_PATH = "/codeInsight/guess/containerElement";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void test1() {
    configureByFile(BASE_PATH + "/Test1.java");
    PsiType[] result = guessContainerElementTypes();
    assertNotNull(result);
    assertEquals(result.length, 1);
    assertTrue(result[0].equalsToText("java.lang.String"));
  }

  public void test2() {
    configureByFile(BASE_PATH + "/Test2.java");
    PsiType[] result = guessContainerElementTypes();
    assertNotNull(result);
    assertEquals(result.length, 1);
    assertTrue(result[0].equalsToText("java.lang.String"));
  }

  private PsiType[] guessContainerElementTypes() {
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement element = getFile().findElementAt(offset);
    assertNotNull(element);
    PsiElement parent = element.getParent();
    assertTrue(parent instanceof PsiReferenceExpression);
    PsiExpression expr = (PsiExpression)parent;
    return GuessManager.getInstance(getProject()).guessContainerElementType(expr, null);
  }
}