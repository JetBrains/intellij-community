/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
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

  public void testDispreferImpls() throws Throwable {
    VfsUtil.saveText(getSourceRoot().createChildDirectory(this, "foo").createChildData(this, "Xxx.java"), "package foo; public class Xxx {}");
    checkPreferredItems(0, "Xxy", "Xxx", "XxxEx", "XxxImpl");
  }

  public void testPreferOwnInnerClasses() throws Throwable {
    checkPreferredItems(0, "YyyXxx", "YyyZzz");
  }

  public void testPreferTopLevelClasses() throws Throwable {
    checkPreferredItems(0, "XxxYyy", "XxzYyy");
  }

  public void testDontDispreferImplsAfterNew() throws Throwable {
    VfsUtil.saveText(getSourceRoot().createChildDirectory(this, "foo").createChildData(this, "Xxx.java"), "package foo; public interface Xxx {}");
    checkPreferredItems(0, "Xxx", "XxxImpl");
  }

  public void testPreferLessHumps() throws Throwable {
    final VirtualFile foo = getSourceRoot().createChildDirectory(this, "foo");
    VfsUtil.saveText(foo.createChildData(this, "XaYa.java"), "package foo; public interface XaYa {}");
    VfsUtil.saveText(foo.createChildData(this, "XyYa.java"), "package foo; public interface XyYa {}");
    checkPreferredItems(0, "XaYa", "XyYa", "XaYaEx", "XaYaImpl", "XyYaXa");
  }

  public void testPreferLessParameters() throws Throwable {
    checkPreferredItems(0, "foo", "foo", "foo", "fox");
    assertEquals(0, ((PsiMethod)myItems[0].getObject()).getParameterList().getParametersCount());
    assertEquals(1, ((PsiMethod)myItems[1].getObject()).getParameterList().getParametersCount());
    assertEquals(2, ((PsiMethod)myItems[2].getObject()).getParameterList().getParametersCount());
  }
  public void testStatsForClassNameInExpression() throws Throwable {
    final VirtualFile foo = getSourceRoot().createChildDirectory(this, "foo");
    VfsUtil.saveText(foo.createChildData(this, "FooBar.java"), "package foo; public interface FooBar {}");
    VfsUtil.saveText(foo.createChildData(this, "FooBee.java"), "package foo; public interface FooBee {}");

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
    final VirtualFile foo = getSourceRoot().createChildDirectory(this, "foo");
    VfsUtil.saveText(foo.createChildData(this, "Foo.java"), "package foo; public interface Foo {}");
    final VirtualFile bar = getSourceRoot().createChildDirectory(this, "bar");
    VfsUtil.saveText(bar.createChildData(this, "Foo.java"), "package bar; public interface Foo {}");

    checkPreferredItems(0, "Foo", "Foo");
    assertEquals("foo.Foo", ((JavaPsiClassReferenceElement)getLookup().getCurrentItem()).getQualifiedName());
  }

  public void testDeclaredMembersGoFirst() throws Exception {
    invokeCompletion(getBasePath() + "/" + getTestName(false) + ".java");
    assertStringItems("fromThis", "overridden", "fromSuper", "equals", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait", "wait", "wait");
  }

}