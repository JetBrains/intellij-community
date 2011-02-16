/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.psi.PsiMethod;

import java.util.List;

@SuppressWarnings({"ALL"})
public class NormalCompletionOrderingTest extends CompletionSortingTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/normalSorting";

  public NormalCompletionOrderingTest() {
    super(CompletionType.BASIC);
  }

  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + BASE_PATH;
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
    final LookupImpl lookup = invokeCompletion(getTestName(false) + ".html");
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
      checkPreferredItems(0, "classLoader", "class", "classBeforeLoader", "clone");
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

  public void testDispreferImpls() throws Throwable {
    myFixture.addClass("package foo; public class Xxx {}");
    checkPreferredItems(0, "Xxx", "XxxEx", "XxxImpl", "Xxy");
  }

  public void testPreferOwnInnerClasses() throws Throwable {
    checkPreferredItems(0, "YyyXxx", "YyyZzz");
  }

  public void testPreferTopLevelClasses() throws Throwable {
    checkPreferredItems(0, "XxxYyy", "XxzYyy");
  }

  public void testDontDispreferImplsAfterNew() throws Throwable {
    myFixture.addClass("package foo; public interface Xxx {}");
    checkPreferredItems(0, "Xxx", "XxxImpl");
  }

  public void testPreferLessHumps() throws Throwable {
    myFixture.addClass("package foo; public interface XaYa {}");
    myFixture.addClass("package foo; public interface XyYa {}");
    checkPreferredItems(0, "XaYa", "XaYaEx", "XaYaImpl", "XyYa", "XyYaXa");
  }

  public void testPreferLessParameters() throws Throwable {
    checkPreferredItems(0, "foo", "foo", "foo", "fox");
    final List<LookupElement> items = getLookup().getItems();
    assertEquals(0, ((PsiMethod)items.get(0).getObject()).getParameterList().getParametersCount());
    assertEquals(1, ((PsiMethod)items.get(1).getObject()).getParameterList().getParametersCount());
    assertEquals(2, ((PsiMethod)items.get(2).getObject()).getParameterList().getParametersCount());
  }
  public void testStatsForClassNameInExpression() throws Throwable {
    myFixture.addClass("package foo; public interface FooBar {}");
    myFixture.addClass("package foo; public interface FooBee {}");

    checkPreferredItems(0, "FooBar", "FooBee");
    incUseCount(getLookup(), 1);
    assertPreferredItems(0, "FooBee", "FooBar");
  }

  public void testDispreferFinalize() throws Throwable {
    checkPreferredItems(0, "final", "finalize");
  }

  public void testPreferNewExpectedInner() throws Throwable {
    checkPreferredItems(0, "Foooo.Bar", "Foooo");

    /*final LookupElementPresentation presentation = new LookupElementPresentation();
    getLookup().getItems().get(0).renderElement(presentation);
    assertEquals("Foooo.Bar", presentation.getItemText());*/
  }

  public void testClassInCallOfItsMethod() throws Throwable {
    myFixture.addClass("package foo; public interface Foo {}");
    myFixture.addClass("package bar; public interface Foo {}");

    checkPreferredItems(0, "Foo", "Foo");
    assertEquals("foo.Foo", ((JavaPsiClassReferenceElement)getLookup().getCurrentItem()).getQualifiedName());
  }

  public void testDeclaredMembersGoFirst() throws Exception {
    invokeCompletion(getTestName(false) + ".java");
    assertStringItems("fromThis", "overridden", "fromSuper", "equals", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait", "wait", "wait");
  }

  public void testLocalVarsOverMethods() {
    checkPreferredItems(0, "value");
  }

  public void testCurrentClassBest() {
    checkPreferredItems(0, "XcodeProjectTemplate", "XcodeConfigurable");
  }

  public void testFqnStats() {
    myFixture.addClass("public interface Baaaaaaar {}");
    myFixture.addClass("package zoo; public interface Baaaaaaar {}");

    final LookupImpl lookup = invokeCompletion(getTestName(false) + ".java");
    assertEquals("Baaaaaaar", ((JavaPsiClassReferenceElement) lookup.getItems().get(0)).getQualifiedName());
    assertEquals("zoo.Baaaaaaar", ((JavaPsiClassReferenceElement) lookup.getItems().get(1)).getQualifiedName());
    incUseCount(lookup, 1);

    assertEquals("zoo.Baaaaaaar", ((JavaPsiClassReferenceElement) lookup.getItems().get(0)).getQualifiedName());
    assertEquals("Baaaaaaar", ((JavaPsiClassReferenceElement) lookup.getItems().get(1)).getQualifiedName());
  }

}