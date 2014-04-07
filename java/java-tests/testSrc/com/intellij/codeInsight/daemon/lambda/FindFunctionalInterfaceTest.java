/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.lambda;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.Collection;

public class FindFunctionalInterfaceTest extends LightCodeInsightFixtureTestCase {
  public void testMethodArgument() throws Exception {
    myFixture.configureByFile(getTestName(false) + ".java");
    final PsiElement elementAtCaret = myFixture.getElementAtCaret();
    assertNotNull(elementAtCaret);
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass.class, false);
    assertTrue(psiClass != null && psiClass.isInterface());
    final Collection<PsiFunctionalExpression> expressions = FunctionalExpressionSearch.search(psiClass).findAll();
    assertTrue(expressions.size() == 1);
    final PsiFunctionalExpression next = expressions.iterator().next();
    assertNotNull(next);
    assertEquals("() -> {}", next.getText());
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/lambda/findUsages/";
  }
}
