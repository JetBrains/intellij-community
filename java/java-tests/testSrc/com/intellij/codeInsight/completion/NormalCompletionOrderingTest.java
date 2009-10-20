/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.psi.PsiMethod;

@SuppressWarnings({"ALL"})
public class NormalCompletionOrderingTest extends CompletionSortingTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/normalSorting";

  public NormalCompletionOrderingTest() {
    super(CompletionType.BASIC);
  }

  protected String getBasePath() {
    return BASE_PATH;
  }

  public void testDontPreferRecursiveMethod() throws Throwable {
    checkPreferredItems(0, "registrar", "register");
  }

  public void testDontPreferRecursiveMethod2() throws Throwable {
    checkPreferredItems(0, "return", "register");
  }

  public void testPreferAnnotationMethods() throws Throwable {
    checkPreferredItems(0, "name", "value", "Foo", "Anno");
  }

  public void testPreferSuperMethods() throws Throwable {
    checkPreferredItems(0, "foo", "bar");
  }

  public void testSubstringVsSubSequence() throws Throwable {
    checkPreferredItems(0, "substring", "substring", "subSequence");
  }

  public void testReturnF() throws Throwable {
    checkPreferredItems(0, "false", "finalize");
  }

  public void testPreferDefaultTypeToExpected() throws Throwable {
    checkPreferredItems(0, "getName", "getNameIdentifier");
  }

  public void testShorterPrefixesGoFirst() throws Throwable {
    final LookupImpl lookup = invokeCompletion(getBasePath() + "/" + getTestName(false) + ".html");
    assertPreferredItems(0, "p", "param", "pre");
    incUseCount(lookup, 2);
    assertPreferredItems(0, "p", "pre", "param");
  }

  public void testShorterShouldBePreselected() throws Throwable {
    checkPreferredItems(0, "foo", "fooLongButOfDefaultType");
  }

  public void testGenericMethodsWithBoundParametersAreStillBetterThanClassLiteral() throws Throwable {
    checkPreferredItems(0, "getService", "getService", "class");
  }

  public void testUppercaseMatters() throws Throwable {
    final int old = CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE;
    try {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER;
      checkPreferredItems(0, "classLoader", "classBeforeLoader", "clone", "class");
    }
    finally {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = old;
    }
  }

  public void testUppercaseMatters2() throws Throwable {
    final int old = CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE;
    try {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.ALL;
      checkPreferredItems(0, "classLoader", "classLoader2");
    }
    finally {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = old;
    }
  }

  public void testHonorUppercaseLetters() throws Throwable {
    checkPreferredItems(0, "clsLoader", "clone", "class");
  }

  public void testClassStaticMembersInVoidContext() throws Throwable {
    checkPreferredItems(0, "booleanMethod", "voidMethod", "AN_OBJECT", "BOOLEAN", "Inner");
  }

  public void testClassStaticMembersInBooleanContext() throws Throwable {
    checkPreferredItems(0, "booleanMethod", "voidMethod", "BOOLEAN", "AN_OBJECT", "Inner");
  }

  public void testDispreferDeclared() throws Throwable {
    checkPreferredItems(0, "aabbb", "aaa");
  }

  public void testDispreferDeclaredOfExpectedType() throws Throwable {
    checkPreferredItems(0, "aabbb", "aaa");
  }

  public void testPreferLessParameters() throws Throwable {
    checkPreferredItems(0, "foo", "foo", "foo", "fox");
    assertEquals(0, ((PsiMethod)myItems[0].getObject()).getParameterList().getParametersCount());
    assertEquals(1, ((PsiMethod)myItems[1].getObject()).getParameterList().getParametersCount());
    assertEquals(2, ((PsiMethod)myItems[2].getObject()).getParameterList().getParametersCount());
  }

}