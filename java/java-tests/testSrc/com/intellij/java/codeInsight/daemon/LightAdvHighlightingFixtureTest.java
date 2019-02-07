// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class LightAdvHighlightingFixtureTest extends LightCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

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

  public void testPackageNamedAsClassInDefaultPackage() {
    myFixture.addClass("package test; public class A {}");
    PsiClass aClass = myFixture.addClass("public class test {}");
    doTest();
    assertNull(ReferencesSearch.search(aClass).findFirst());
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

    List<HighlightInfo> highlights = CodeInsightTestFixtureImpl.instantiateAndRun(fragment, editor, ArrayUtil.EMPTY_INT_ARRAY, false);
    List<HighlightInfo> problems = DaemonAnalyzerTestCase.filter(highlights, HighlightSeverity.WARNING);
    assertThat(problems).isEmpty();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}