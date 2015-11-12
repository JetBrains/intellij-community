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
import com.intellij.psi.*;
import com.intellij.psi.impl.search.JavaFunctionalExpressionSearcher;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Predicate;

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

  public void testMethodArgumentByTypeParameter() throws Exception {
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

  public void testFieldFromAnonymousClassScope() throws Exception {
    myFixture.configureByFile(getTestName(false) + ".java");
    final PsiElement elementAtCaret = myFixture.getElementAtCaret();
    assertNotNull(elementAtCaret);
    final PsiField field = PsiTreeUtil.getParentOfType(elementAtCaret, PsiField.class, false);
    assertNotNull(field);
    final PsiClass aClass = field.getContainingClass();
    assertTrue(aClass instanceof PsiAnonymousClass);
    final Collection<PsiReference> references = ReferencesSearch.search(field).findAll();
    assertFalse(references.isEmpty());
    assertEquals(1, references.size());
  }

  public void testClassFromJdk() {
    doTestIndexSearch("(e) -> true");
  }

  public void testClassFromJdkMethodRef() {
    doTestIndexSearch("this::bar");
  }

  public void doTestIndexSearch(String expected) {
    myFixture.configureByFile(getTestName(false) + ".java");

    for (int i = 0; i < JavaFunctionalExpressionSearcher.SMART_SEARCH_THRESHOLD + 5; i++) {
      myFixture.addFileToProject("a" + i + ".java", "class Goo {{ Runnable r = () -> {} }}");
    }

    PsiClass predicate = JavaPsiFacade.getInstance(getProject()).findClass(Predicate.class.getName(), GlobalSearchScope.allScope(getProject()));
    assert predicate != null;
    final PsiFunctionalExpression next = assertOneElement(FunctionalExpressionSearch.search(predicate).findAll());
    assertEquals(expected, next.getText());
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/lambda/findUsages/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
