// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl.search;

import com.intellij.JavaTestUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.search.JavaFunctionalExpressionSearcher;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.CommonProcessors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.function.Predicate;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class FindFunctionalInterfaceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/lambda/findUsages/");
  }

  @Test
  public void testMethodArgument() {
    doTestOneExpression();
  }

  @Test
  public void testMethodArgumentByTypeParameter() {
    doTestOneExpression();
  }

  @Test
  public void testFieldDeclaredInFileWithoutFunctionalInterfaces() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("class B {" +
                       "  void f(A a) {" +
                       "    a.r = () -> {};" +
                       "  }" +
                       "}");
    myFixture.addClass("public class A {" +
                       "  public I r;" +
                       "}");

    doTestOneExpression();
  }

  @Test
  public void testVarargPosition() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""

                         class A { \s
                           <T> void foo(T... r) {}
                           void bar(J i){foo(i, i, () -> {});}
                         }""");

    doTestOneExpression();
  }

  private void doTestOneExpression() {
    configure();
    ReadAction.runBlocking(() -> {
      final PsiClass psiClass = findClassAtCaret();
      final Collection<PsiFunctionalExpression> expressions =
        FunctionalExpressionSearch.search(psiClass).findAll();
      int size = expressions.size();
      assertEquals(1, size);
      final PsiFunctionalExpression next = expressions.iterator().next();
      assertNotNull(next);
      assertEquals("() -> {}", next.getText());
    });
  }

  @NotNull
  private PsiClass findClassAtCaret() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return ReadAction.computeBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getElementAtCaret();
      assertNotNull(elementAtCaret);
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass.class, false);
      assertTrue(psiClass != null && psiClass.isInterface());
      return psiClass;
    });
  }

  @Test
  public void testFieldFromAnonymousClassScope() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure();
    ReadAction.runBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getElementAtCaret();
      assertNotNull(elementAtCaret);
      final PsiField field = PsiTreeUtil.getParentOfType(elementAtCaret, PsiField.class, false);
      assertNotNull(field);
      final PsiClass aClass = field.getContainingClass();
      assertTrue(aClass instanceof PsiAnonymousClass);
      final Collection<PsiReference> references = ReferencesSearch.search(field).findAll();
      assertFalse(references.isEmpty());
      assertEquals(1, references.size());
    });
  }

  @Test
  public void testMethodWithClassTypeParameter() {
    configure();
    ReadAction.runBlocking(() -> {
      assertThat(FunctionalExpressionSearch.search(findClass("I")).findAll()).hasSize(1);
    });
  }

  @Test
  public void testFindSubInterfaceLambdas() {
    configure();

    ReadAction.runBlocking(() -> {
      assertThat(FunctionalExpressionSearch.search(findClass("DumbAwareRunnable")).findAll()).hasSize(5);
      assertThat(FunctionalExpressionSearch.search(findClass("DumbAwareRunnable2")).findAll()).hasSize(3);
      assertThat(FunctionalExpressionSearch.search(findClass("DumbAware")).findAll()).hasSize(6);

      assertThat(FunctionalExpressionSearch.search(findClass("WithDefaultMethods")).findAll()).hasSize(1);
      assertThat(FunctionalExpressionSearch.search(findClass("WithManyMethods")).findAll()).hasSize(1);
      assertThat(FunctionalExpressionSearch.search(findClass("WithManyMethods2")).findAll()).hasSize(1);
    });
  }

  @Test
  public void testArraysStreamLikeApi() {
    configure();
    ReadAction.runBlocking(() -> {
      assertThat(FunctionalExpressionSearch.search(findClass("I")).findAll()).hasSize(1);
    });
  }

  @Test
  public void testStreamOfLikeApiWithLocalVar() {
    configure();
    ReadAction.runBlocking(() -> {
      assertThat(FunctionalExpressionSearch.search(findClass("I")).findAll()).hasSize(1);
    });
  }

  @Test
  public void testDefaultInHierarchy() {
    configure();
    ReadAction.runBlocking(() -> {
      assertThat(FunctionalExpressionSearch.search(findClass("I").getMethods()[0]).findAll()).isNullOrEmpty();
    });
  }

  @Test
  public void testStreamOfLikeApiWithField() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("class Base { StrType Stream = null; }");
    configure();
    ReadAction.runBlocking(() -> {
      assertThat(FunctionalExpressionSearch.search(findClass("I")).findAll()).hasSize(1);
    });
  }

  @Test
  public void testCallWithQualifiedName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package pkg.p1.p2.p3; public interface I { void run() {} }");
    myFixture.addClass("package pkg.p1.p2.p3; public class Util { public static void foo(I i) {} }");
    configure();
    ReadAction.runBlocking(() -> {
      assertThat(FunctionalExpressionSearch.search(findClass("pkg.p1.p2.p3.I")).findAll()).hasSize(1);
    });
  }

  @Test
  public void testInsideArrayInitializer() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("public interface Foo { void run() {}}");
    myFixture.addClass("public interface Bar { void run() {}}");
    configure();
    ReadAction.runBlocking(() -> {
      assertThat(FunctionalExpressionSearch.search(findClass("Foo")).findAll()).hasSize(3);
    });
  }

  @Test
  public void testCallOnGenericParameter() {
    configure();
    ReadAction.runBlocking(() -> {
      assertThat(FunctionalExpressionSearch.search(findClass("I")).findAll()).hasSize(1);
    });
  }

  @Test
  public void testChainStartingWithConstructor() {
    configure();
    ReadAction.runBlocking(() -> {
      assertThat(FunctionalExpressionSearch.search(findClass("IterHelper.MapIterCallback")).findAll()).hasSize(1);
    });
  }

  @Test
  public void testDontVisitInapplicableFiles() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass sam = myFixture.addClass("interface I { void foo(); }");
    myFixture.addClass("class Some { " +
                       "{ I i = () -> {}; }" +
                       "void doTest(int a) {} " +
                       "void doTest(I i, I j) {} " +
                       "Some intermediate() {} " +
                       "Object intermediate(int a, int b) {} " +
                       "}");
    myFixture.addClass("class _WrongSignature {{ I i = a -> {}; I j = () -> true; }}");
    myFixture.addClass("class _CallArgumentCountMismatch extends Some {{ " +
                       "  doTest(() -> {}); " +
                       "  intermediate(4).doTest(() -> {}, () -> {}); " +
                       "}}");
    myFixture.addClass("class _KnownTypeVariableAssignment {" +
                       "static Runnable field;" +
                       "{ Runnable r = () -> {}; field = () -> {}; } " +
                       "}");
    myFixture.addClass("class _SuperFieldAssignment extends _KnownTypeVariableAssignment {" +
                       "{ field = () -> {}; } " +
                       "}");
    myFixture.addClass("import static _KnownTypeVariableAssignment.*; " +
                       "class _StaticallyImportedFieldAssignment {" +
                       "{ field = () -> {}; } " +
                       "}");

    ReadAction.runBlocking(() -> {
      CommonProcessors.CollectProcessor<PsiFunctionalExpression> result = new CommonProcessors.CollectProcessor<>();
      JavaFunctionalExpressionSearcher.Session session = new JavaFunctionalExpressionSearcher.Session(
        new FunctionalExpressionSearch.SearchParameters(sam, GlobalSearchScope.allScope(myFixture.getProject())),
        result
      );
      session.processResults();
      assertThat(result.getResults()).hasSize(1);
      for (VirtualFile file : session.getFilesLookedInside()) {
        assertFalse(file.getName().startsWith("_"), file.getName());
      }
    });
  }

  @Test
  public void testReturnedFunExpressionsDoNotHoldAst() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass sam = myFixture.addClass("interface I { void foo(); }");
    PsiFile usages = myFixture.addFileToProject("Some.java", "class Some { " +
                                                              "{ I[] is = { () -> {}, this::toString }; }" +
                                                              "}");
    ReadAction.runBlocking(() -> {
      assertFalse(((PsiFileImpl) usages).isContentsLoaded());
      assertNull(((PsiFileImpl) usages).derefStub());
    });

    Collection<PsiFunctionalExpression> all = ReadAction.computeBlocking(() -> FunctionalExpressionSearch.search(sam).findAll());
    assertThat(all).hasSize(2);
    for (PsiFunctionalExpression expression : all) {
      LeakHunter.checkLeak(expression, ASTNode.class);
    }
  }

  @Test
  public void testNoAstLoadingInObviousCases() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass sam = myFixture.addClass("interface I { void foo(); }");
    PsiFile usages = myFixture.addFileToProject("Some.java", "class Some { " +
                                                              "void bar(I i) {}" +
                                                             "{ bar(() -> {}); }; }" +
                                                              "}");
    ReadAction.runBlocking(() -> {
      assertThat(FunctionalExpressionSearch.search(sam).findAll()).hasSize(1);
      assertFalse(((PsiFileImpl) usages).isContentsLoaded());
    });
  }

  @Test
  public void testLambdaInSecondFieldOfSharedDeclaration() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass sam = myFixture.addClass("interface I { void run(); }");
    PsiFile usages = myFixture.addFileToProject("Some.java", """
      class Some {
        I first = null, second = () -> {};
      }
      """);
    ReadAction.runBlocking(() -> {
      assertFalse(((PsiFileImpl)usages).isContentsLoaded());

      assertThat(FunctionalExpressionSearch.search(sam).findAll()).hasSize(1);
    });
  }

  private PsiClass findClass(String fqName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return ReadAction.computeBlocking(() -> myFixture.findClass(fqName));
  }

  private void configure() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testClassFromJdk() {
    doTestIndexSearch("(e) -> true");
  }

  @Test
  public void testClassFromJdkMethodRef() {
    doTestIndexSearch("this::bar");
  }

  public void doTestIndexSearch(String expected) {
    configure();

    ReadAction.runBlocking(() -> {
      PsiClass predicate = findClass(Predicate.class.getName());
      final PsiFunctionalExpression next =
        assertThat(FunctionalExpressionSearch.search(predicate).findAll()).singleElement().actual();
      assertEquals(expected, next.getText());
    });
  }

  @Test
  public void testConstructorReferences() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure();

    myFixture.addClass("""
                         class Bar extends Foo {
                           public Bar() { super(() -> 1); }

                           {
                             new Foo(() -> 2) { };
                             new Foo(() -> 3);
                           }
                         }""");

    ReadAction.runBlocking(() -> {
      assertThat(FunctionalExpressionSearch.search(findClassAtCaret()).findAll()).hasSize(5);
    });
  }

  @Test
  public void testFindLambdaForAllEquivalentSams() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure();

    PsiFile file = myFixture.addFileToProject("a.java",
                                              "interface Foo { void foo(); }" +
                                              "interface Foo { void bar(); } ");
    ReadAction.runBlocking(() -> {
      PsiClass[] fooClasses = ((PsiJavaFile)file).getClasses();
      assertThat(FunctionalExpressionSearch.search(fooClasses[0]).findAll()).hasSize(1);
      assertThat(FunctionalExpressionSearch.search(fooClasses[1]).findAll()).hasSize(1);
    });
  }

  @Test
  public void testInvalidCode() {
    configure();
    // whatever, but it shouldn't throw
    ReadAction.runBlocking(() -> {
      assertThat(FunctionalExpressionSearch.search(findClass("I")).findAll()).isNullOrEmpty();
    });
  }

  @Test
  public void testNoCrashInDumbMode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass sam = myFixture.addClass("interface I { void foo(); }");
    myFixture.addClass("class Some {{ I i = () -> {}; }}");

    assertThat(ReadAction.computeBlocking(() -> FunctionalExpressionSearch.search(sam).findAll())).hasSize(1);

    DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> {
      assertThat(ReadAction.computeBlocking(() -> FunctionalExpressionSearch.search(sam).findAll())).isNullOrEmpty();
    });
  }
}
