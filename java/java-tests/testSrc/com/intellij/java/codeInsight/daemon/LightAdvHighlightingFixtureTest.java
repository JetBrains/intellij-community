// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtilRt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class LightAdvHighlightingFixtureTest extends LightJavaCodeInsightFixtureTestCase {
  

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advFixture";
  }

  public void testHidingOnDemandImports() {
    //noinspection StaticNonFinalField
    myFixture.addClass("package foo; public class Foo {" +
                       "  public static String foo;" +
                       "}");
    myFixture.addClass("package foo; public class Bar {" +
                       "  public static void foo(String s) {}" +
                       "}");
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting(false, false, false);
  }

  public void testFilteredCandidates() {
    PsiFile file = myFixture.configureByText("a.java", "class a {{new StringBuilder().ap<caret>pend();}}");
    PsiCallExpression callExpression =
      PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getEditor().getCaretModel().getOffset()), PsiCallExpression.class);
    assertNotNull(callExpression);
    CandidateInfo[] candidates =
      PsiResolveHelper.SERVICE.getInstance(myFixture.getProject()).getReferencedMethodCandidates(callExpression, false);
    assertSize(27, candidates);
    String generateDoc = new JavaDocumentationProvider().generateDoc(callExpression, callExpression);
    assertEquals("<html>Candidates for method call <b>new StringBuilder().append()</b> are:<br><br>&nbsp;&nbsp;" +
                 "<a href=\"psi_element://java.lang.StringBuilder#append(java.lang.Object)\">StringBuilder append(Object)</a><br>&nbsp;&nbsp;" +
                 "<a href=\"psi_element://java.lang.StringBuilder#append(java.lang.String)\">StringBuilder append(String)</a><br>&nbsp;&nbsp;" +
                 "<a href=\"psi_element://java.lang.StringBuilder#append(java.lang.StringBuilder)\">StringBuilder append(StringBuilder)</a><br>&nbsp;&nbsp;" +
                 "<a href=\"psi_element://java.lang.StringBuilder#append(java.lang.StringBuffer)\">StringBuilder append(StringBuffer)</a><br>&nbsp;&nbsp;" +
                 "<a href=\"psi_element://java.lang.StringBuilder#append(java.lang.CharSequence)\">StringBuilder append(CharSequence)</a><br>&nbsp;&nbsp;" +
                 "<a href=\"psi_element://java.lang.StringBuilder#append(java.lang.CharSequence, int, int)\">StringBuilder append(CharSequence, int, int)</a><br>&nbsp;&nbsp;" +
                 "<a href=\"psi_element://java.lang.StringBuilder#append(char[])\">StringBuilder append(char[])</a><br>&nbsp;&nbsp;" +
                 "<a href=\"psi_element://java.lang.StringBuilder#append(char[], int, int)\">StringBuilder append(char[], int, int)</a><br>&nbsp;&nbsp;" +
                 "<a href=\"psi_element://java.lang.StringBuilder#append(boolean)\">StringBuilder append(boolean)</a><br>&nbsp;&nbsp;" +
                 "<a href=\"psi_element://java.lang.StringBuilder#append(char)\">StringBuilder append(char)</a><br>&nbsp;&nbsp;" +
                 "<a href=\"psi_element://java.lang.StringBuilder#append(int)\">StringBuilder append(int)</a><br>&nbsp;&nbsp;" +
                 "<a href=\"psi_element://java.lang.StringBuilder#append(long)\">StringBuilder append(long)</a><br>&nbsp;&nbsp;" +
                 "<a href=\"psi_element://java.lang.StringBuilder#append(float)\">StringBuilder append(float)</a><br>&nbsp;&nbsp;" +
                 "<a href=\"psi_element://java.lang.StringBuilder#append(double)\">StringBuilder append(double)</a><br></html>", generateDoc);
  }

  public void testPackageNamedAsClassInDefaultPackage() {
    myFixture.addClass("package test; public class A {}");
    PsiClass aClass = myFixture.addClass("public class test {}");
    doTest();
    assertNull(ProgressManager.getInstance().runProcess(() -> ReferencesSearch.search(aClass).findFirst(), new EmptyProgressIndicator()));
  }

  public void testPackageNameAsClassFQName() {
    myFixture.addClass("package foo.Bar; class A {}");
    myFixture.addClass("package foo; public class Bar { public static class Inner {}}");
    doTest();
  }

  public void testInaccessibleFunctionalTypeParameter() {
    myFixture.addClass("package test; class A {}");
    myFixture.addClass("package test; public interface I { void m(A a);}");
    myFixture.addClass("package test; public interface J { A m();}");
    doTest();
  }

  public void testBoundsPromotionWithCapturedWildcards() {
    myFixture.addClass("package a; public interface Provider<A> {}");
    myFixture.addClass("package b; public interface Provider<B> {}");
    doTest();
  }

  public void testStaticImportCompoundWithInheritance() {
    myFixture.addClass("package a; public interface A { static void foo(Object o){} static void foo(String str) {}}");
    doTest();
  }

  public void testSuppressedInGenerated() {
    myFixture.enableInspections(new RedundantCastInspection());
    myFixture.addClass("package javax.annotation; public @interface Generated {}");
    doTest();
  }

  public void testReferenceThroughInheritance() {
    myFixture.addClass("package test;\n" +
                       "public class A {\n" +
                       "  public static class B {}\n" +
                       "}");
    doTest();
  }

  public void testReferenceThroughInheritance1() {
    //noinspection UnnecessaryInterfaceModifier
    myFixture.addClass("package me;\n" +
                       "import me.Serializer.Format;\n" +
                       "public interface Serializer<F extends Format> {\n" +
                       "    public static interface Format {}\n" +
                       "}\n");
    doTest();
  }

  public void testUsageOfProtectedAnnotationOutsideAPackage() {
    myFixture.addClass("package a;\n" +
                       "import java.lang.annotation.ElementType;\n" +
                       "import java.lang.annotation.Target;\n" +
                       "\n" +
                       "public class A {\n" +
                       "    @Target( { ElementType.METHOD, ElementType.TYPE } )\n" +
                       "    protected @interface Test{\n" +
                       "    }\n" +
                       "}");
    doTest();
  }

  public void testPackageLocalClassUsedInArrayTypeOutsidePackage() {
    myFixture.addClass("package a; class A {}");
    myFixture.addClass("package a; public class B {public static A[] getAs() {return null;}}");
    doTest();
  }

  public void testProtectedFieldUsedInAnnotationParameterOfInheritor() {
    myFixture.addClass("package a; public class A {protected final static String A_FOO = \"A\";}");
    doTest();
  }

  public void testStaticImportClassConflictingWithPackageName() {
    myFixture.addClass("package p.P1; class Unrelated {}");
    myFixture.addClass("package p; public class P1 {public static final int FOO = 1;}");
    doTest();
  }

  public void testAmbiguousMethodCallWhenStaticImported() {
    myFixture.addClass("package p;" +
                       "class A<K> {\n" +
                       "  static <T> A<T> of(T t) {\n" +
                       "    return null;\n" +
                       "  }\n" +
                       "}\n" +
                       "class B<K> {\n" +
                       "  static <T> B<T> of(T t) {\n" +
                       "    return null;\n" +
                       "  }\n" +
                       "  static <T> B<T> of(T... t) {\n" +
                       "    return null;\n" +
                       "  }\n" +
                       "}\n");
    doTest();
  }

  public void testClassPackageConflict() {
    myFixture.addClass("package a; public class b {}");
    myFixture.addClass("package c; public class a {}");
    doTest();
  }

  public void testClassPackageConflict1() {
    myFixture.addClass("package a; public class b {}");
    myFixture.addClass("package c.d; public class a {}");
    doTest();
  }

  public void testTypeAnnotations() {
    myFixture.addClass("import java.lang.annotation.ElementType;\n" +
                       "import java.lang.annotation.Target;\n" +
                       "@Target({ElementType.TYPE_USE})\n" +
                       "@interface Nullable {}\n");
    myFixture.addClass("class Middle<R> extends Base<@Nullable R, String>{}");
    myFixture.addClass("class Child<R> extends Middle<R>{}");
    PsiClass baseClass = myFixture.addClass("class Base<R, C> {}");
    PsiClass fooClass = myFixture.addClass("class Foo {\n" +
                                           "  Child<String> field;\n" +
                                           "}");
    PsiField fooField = fooClass.findFieldByName("field", false);
    PsiType substituted =
      TypeConversionUtil.getSuperClassSubstitutor(baseClass, (PsiClassType)fooField.getType()).substitute(baseClass.getTypeParameters()[0]);
    assertEquals(1, substituted.getAnnotations().length);
  }

  public void testCodeFragmentMayAccessDefaultPackage() {
    myFixture.addClass("public class MainClass { }");

    Project project = getProject();
    PsiElement context = JavaPsiFacade.getInstance(project).findPackage("");
    JavaCodeFragment fragment = JavaCodeFragmentFactory.getInstance(project).createReferenceCodeFragment("MainClass", context, true, true);
    Document document = PsiDocumentManager.getInstance(project).getDocument(fragment);
    Editor editor = EditorFactory.getInstance().createViewer(document, project);
    Disposer.register(myFixture.getTestRootDisposable(), () -> EditorFactory.getInstance().releaseEditor(editor));

    List<HighlightInfo> highlights = CodeInsightTestFixtureImpl.instantiateAndRun(fragment, editor, ArrayUtilRt.EMPTY_INT_ARRAY, false);
    List<HighlightInfo> problems = DaemonAnalyzerTestCase.filter(highlights, HighlightSeverity.WARNING);
    assertThat(problems).isEmpty();
  }

  public void testImplicitConstructorAccessibility() {
    myFixture.addClass("package a; public class Base {" +
                       "private Base() {}\n" +
                       "protected Base(int... i) {}\n" +
                       "}");
    doTest();
  }

  public void testDiamondsWithAnonymousProtectedConstructor() {
    myFixture.addClass("package a; public class Base<T> { protected Base() {}}");
    doTest();
  }
  
  public void testDiamondsWithProtectedCallInConstruction() {
    myFixture.addClass("package a; public class Base { " +
                       " protected String createString() {return null;}" +
                       "}");
    doTest();
  }

  public void testDefaultAnnotationsApplicability() {
    myFixture.addClass("package foo; public @interface A {}");
    myFixture.configureByFile("module-info.java");
    myFixture.checkHighlighting();
  }

  public void testAlwaysFalseForLoop() {
    doTest();
    IntentionAction action = myFixture.findSingleIntention("Remove 'for' statement");
    myFixture.launchAction(action);
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}