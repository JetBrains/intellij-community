/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.psi.PsiClass;
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
    checkPreferredItems(0, "false");
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

  public void testShorterShouldBePreselected() throws Throwable {
    checkPreferredItems(0, "foo", "fooLongButOfDefaultType");
  }

  public void testGenericMethodsWithBoundParametersAreStillBetterThanClassLiteral() throws Throwable {
    checkPreferredItems(0, "getService", "getService", "class");
  }

  public void testClassStaticMembersInVoidContext() throws Throwable {
    checkPreferredItems(0, "booleanMethod", "voidMethod", "AN_OBJECT", "BOOLEAN", "class");
  }

  public void testClassStaticMembersInBooleanContext() throws Throwable {
    final String path = getTestName(false) + ".java";
    myFixture.configureByFile(path);
    myFixture.complete(CompletionType.BASIC, 2);
    assertPreferredItems(0, "booleanMethod", "voidMethod", "registerNatives", "BOOLEAN", "AN_OBJECT");
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
    assertEquals("Baaaaaaar", ((JavaPsiClassReferenceElement)lookup.getItems().get(1)).getQualifiedName());
  }

  public void testDispreferInnerClasses() {
    checkPreferredItems(0); //no chosen items
    assertFalse(getLookup().getItems().get(0).getObject() instanceof PsiClass);
  }

  public void testPreferSameNamedMethods() {
    checkPreferredItems(0, "foo", "boo", "doo", "hashCode");
  }

  public void testPreferClassOverItsStaticMembers() {
    checkPreferredItems(0, "Zoo");
  }

  public void testPreferReturn() {
    checkPreferredItems(0, "return", "rLocal", "rParam", "rMethod");
  }

  public void testPreferModifiers() {
    checkPreferredItems(0, "private", "protected", "public", "paaa", "paab");
  }

  public void testPreferElse() {
    checkPreferredItems(0, "else", "element");
  }

  public void testPreferMoreMatching() {
    checkPreferredItems(0, "FooOCSomething", "FooObjectCollector");
  }

  public void testPreselectMostRelevantInTheMiddle() {
    myFixture.addClass("package foo; public class Elaaaaaaaaaaaaaaaaaaaa {}");
    invokeCompletion(getTestName(false) + ".java");
    LookupImpl lookup = getLookup();
    assertPreferredItems(lookup.getList().getSelectedIndex());
    assertEquals("Elaaaaaaaaaaaaaaaaaaaa", lookup.getItems().get(0).getLookupString());
    assertEquals("ELEMENT_A", lookup.getCurrentItem().getLookupString());
  }

  public void testPreferSamePackageOverImported() {
    myFixture.addClass("package bar; public class Bar1 {}");
    myFixture.addClass("package bar; public class Bar2 {}");
    myFixture.addClass("package bar; public class Bar3 {}");
    myFixture.addClass("package bar; public class Bar4 {}");
    myFixture.addClass("class Bar9 {}");
    myFixture.addClass("package doo; public class Bar0 {}");

    checkPreferredItems(0, "Bar9", "Bar1", "Bar2", "Bar3", "Bar4");
  }

  public void testSortSameNamedVariantsByProximity() {
    myFixture.addClass("public class Bar {}");
    for (int i = 0; i < 10; i++) {
      myFixture.addClass("public class Bar" + i + " {}");
      myFixture.addClass("public class Bar" + i + "Colleague {}");
    }
    myFixture.addClass("package bar; public class Bar {}");
    final String path = getTestName(false) + ".java";
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC);
    assertPreferredItems(0, "Bar", "Bar");
    List<LookupElement> items = getLookup().getItems();
    assertEquals(((JavaPsiClassReferenceElement)items.get(0)).getQualifiedName(), "Bar");
    assertEquals(((JavaPsiClassReferenceElement)items.get(1)).getQualifiedName(), "bar.Bar");
  }

  public void testCaseInsensitivePrefixMatch() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    try {
      checkPreferredItems(0, "Foo", "foo1", "foo2");
    }
    finally {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER;
    }
  }

}
