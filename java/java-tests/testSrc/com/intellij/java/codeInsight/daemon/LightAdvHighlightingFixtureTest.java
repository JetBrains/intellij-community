// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class LightAdvHighlightingFixtureTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advFixture");
  }

  @Test
  public void testHidingOnDemandImports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //noinspection StaticNonFinalField
    myFixture.addClass("package foo; public class Foo {" +
                       "  public static String foo;" +
                       "}");
    myFixture.addClass("package foo; public class Bar {" +
                       "  public static void foo(String s) {}" +
                       "}");
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting(false, false, false);
  }

  @Test
  public void testFilteredCandidates() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("a.java", "class a {{new StringBuilder().ap<caret>pend();}}");
    ReadAction.runBlocking(() -> {
      PsiCallExpression callExpression =
        PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getEditor().getCaretModel().getOffset()), PsiCallExpression.class);
      assertNotNull(callExpression);
      CandidateInfo[] candidates =
        PsiResolveHelper.getInstance(myFixture.getProject()).getReferencedMethodCandidates(callExpression, false);
      assertThat(candidates).hasSize(27);
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
    });
  }

  @Test
  public void testPackageNamedAsClassInDefaultPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package test; public class A {}");
    PsiClass aClass = myFixture.addClass("public class test {}");
    doTest();
    assertNull(ProgressManager.getInstance().runProcess(() -> ReferencesSearch.search(aClass).findFirst(), new EmptyProgressIndicator()));
  }

  @Test
  public void testPackageNameAsClassFQName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo.Bar; class A {}");
    myFixture.addClass("package foo; public class Bar { public static class Inner {}}");
    doTest();
  }

  @Test
  public void testInaccessibleFunctionalTypeParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package test; class A {}");
    myFixture.addClass("package test; public interface I { void m(A a);}");
    myFixture.addClass("package test; public interface J { A m();}");
    doTest();
  }

  @Test
  public void testBoundsPromotionWithCapturedWildcards() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package a; public interface Provider<A> {}");
    myFixture.addClass("package b; public interface Provider<B> {}");
    doTest();
  }

  @Test
  public void testStaticImportCompoundWithInheritance() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package a; public interface A { static void foo(Object o){} static void foo(String str) {}}");
    doTest();
  }

  @Test
  public void testSuppressedInGenerated() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new RedundantCastInspection());
    myFixture.addClass("package javax.annotation; public @interface Generated {}");
    doTest();
  }

  @Test
  public void testReferenceThroughInheritance() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package test;
                         public class A {
                           public static class B {}
                         }""");
    doTest();
  }

  @Test
  public void testReferenceThroughInheritance1() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //noinspection UnnecessaryInterfaceModifier
    myFixture.addClass("""
                         package me;
                         import me.Serializer.Format;
                         public interface Serializer<F extends Format> {
                             public static interface Format {}
                         }
                         """);
    doTest();
  }

  @Test
  public void testUsageOfProtectedAnnotationOutsideAPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package a;
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;

                         public class A {
                             @Target( { ElementType.METHOD, ElementType.TYPE } )
                             protected @interface Test{
                             }
                         }""");
    doTest();
  }

  @Test
  public void testPackageLocalClassUsedInArrayTypeOutsidePackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package a; class A {}");
    myFixture.addClass("package a; public class B {public static A[] getAs() {return null;}}");
    doTest();
  }

  @Test
  public void testProtectedFieldUsedInAnnotationParameterOfInheritor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package a; public class A {protected final static String A_FOO = \"A\";}");
    doTest();
  }

  @Test
  public void testStaticImportClassConflictingWithPackageName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package p.P1; class Unrelated {}");
    myFixture.addClass("package p; public class P1 {public static final int FOO = 1;}");
    doTest();
  }

  @Test
  public void testAmbiguousMethodCallWhenStaticImported() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package p;class A<K> {
                           static <T> A<T> of(T t) {
                             return null;
                           }
                         }
                         class B<K> {
                           static <T> B<T> of(T t) {
                             return null;
                           }
                           static <T> B<T> of(T... t) {
                             return null;
                           }
                         }
                         """);
    doTest();
  }

  @Test
  public void testClassPackageConflict() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package a; public class b {}");
    myFixture.addClass("package c; public class a {}");
    doTest();
  }

  @Test
  public void testClassPackageConflict1() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package a; public class b {}");
    myFixture.addClass("package c.d; public class a {}");
    doTest();
  }

  @Test
  public void testCodeFragmentMayAccessDefaultPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("public class MainClass { }");

    Project project = myFixture.getProject();
    JavaCodeFragment fragment = JavaCodeFragmentFactory.getInstance(project).createReferenceCodeFragmentInPackage("MainClass", "", true);
    Document document = ReadAction.computeBlocking(() -> PsiDocumentManager.getInstance(project).getDocument(fragment));
    Editor editor = EdtTestUtil.runInEdtAndGet(() -> EditorFactory.getInstance().createViewer(document, project));
    Disposer.register(myFixture.getTestRootDisposable(), () -> EditorFactory.getInstance().releaseEditor(editor));

    List<HighlightInfo> highlights = CodeInsightTestFixtureImpl.instantiateAndRun(fragment, editor, ArrayUtilRt.EMPTY_INT_ARRAY, false);
    List<HighlightInfo> problems = DaemonAnalyzerTestCase.filter(highlights, HighlightSeverity.WARNING);
    assertThat(problems).isEmpty();
  }

  @Test
  public void testImplicitConstructorAccessibility() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package a; public class Base {private Base() {}
                         protected Base(int... i) {}
                         }""");
    doTest();
  }

  @Test
  public void testDiamondsWithAnonymousProtectedConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package a; public class Base<T> { protected Base() {}}");
    doTest();
  }
  
  @Test
  public void testDiamondsWithProtectedCallInConstruction() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package a; public class Base { " +
                       " protected String createString() {return null;}" +
                       "}");
    doTest();
  }

  @Test
  public void testDefaultAnnotationsApplicability() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public @interface A {}");
    myFixture.configureByFile("module-info.java");
    myFixture.checkHighlighting();
  }

  @Test
  public void testAlwaysFalseForLoop() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest();
    IntentionAction action = myFixture.findSingleIntention("Remove 'for' statement");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(action);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testProtectedInnerClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package a;
                         public class Outer {
                          public Object get(Inner key) {
                           return null;
                          }
                          public Inner get1() {return null;}\s
                          public Inner f;\s
                          protected class Inner {}
                         }""");
    doTest();
  }
  
  @Test
  public void testProtectedInnerClass1() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package a;
                         public class A<T> {
                           public T getData() {return null;}
                         }""");
    myFixture.addClass("""
                         package a;
                         public class Outer extends A<Outer.Inner> {
                          protected class Inner {}
                         }""");
    doTest();
  }

  @Test
  public void testGenericParameterAnotherPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get(); // IDEA-352818
    myFixture.addClass("""
      package foo;
      
      import java.util.List;
      
      @SuppressWarnings("ClassEscapesDefinedScope")
      public final class Foo {
          public static List<PackagePrivate> foo() {
              return List.of(new PackagePrivate());
          }
      
          static class PackagePrivate implements Runnable {
              @Override
              public void run() {
                  System.out.println("PackagePrivate.run");
              }
          }
      }""");
    myFixture.configureByText("Bar.java", """
      package bar;
      
      import foo.Foo;
      
      import java.util.List;
      
      final class Bar {
        public static void main(String[] args) {
          Runnable first = getFirst(Foo.foo());
          first.run();
        }
      
        private static <T> T getFirst(List<T> list) {
          return list.get(0);
        }
      }""");
    myFixture.checkHighlighting();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}