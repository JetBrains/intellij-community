// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.JavaTestUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.JavaFunctionalExpressionSearcher;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.CommonProcessors;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Predicate;

public class FindFunctionalInterfaceTest extends LightJavaCodeInsightFixtureTestCase {
  public void testMethodArgument() {
    doTestOneExpression();
  }

  public void testMethodArgumentByTypeParameter() {
    doTestOneExpression();
  }

  public void testFieldDeclaredInFileWithoutFunctionalInterfaces() {
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

  public void testVarargPosition() {
    myFixture.addClass("\n" +
                       "class A {  \n" +
                       "  <T> void foo(T... r) {}\n" +
                       "  void bar(J i){foo(i, i, () -> {});}\n" +
                       "}");

    doTestOneExpression();
  }

  private void doTestOneExpression() {
    configure();
    final PsiClass psiClass = findClassAtCaret();
    final Collection<PsiFunctionalExpression> expressions = FunctionalExpressionSearch.search(psiClass).findAll();
    int size = expressions.size();
    assertEquals(1, size);
    final PsiFunctionalExpression next = expressions.iterator().next();
    assertNotNull(next);
    assertEquals("() -> {}", next.getText());
  }

  @NotNull
  private PsiClass findClassAtCaret() {
    final PsiElement elementAtCaret = myFixture.getElementAtCaret();
    assertNotNull(elementAtCaret);
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass.class, false);
    assertTrue(psiClass != null && psiClass.isInterface());
    return psiClass;
  }

  public void testFieldFromAnonymousClassScope() {
    configure();
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

  public void testMethodWithClassTypeParameter() {
    configure();
    assertSize(1, FunctionalExpressionSearch.search(findClass("I")).findAll());
  }

  public void testFindSubInterfaceLambdas() {
    configure();

    assertSize(5, FunctionalExpressionSearch.search(findClass("DumbAwareRunnable")).findAll());
    assertSize(3, FunctionalExpressionSearch.search(findClass("DumbAwareRunnable2")).findAll());
    assertSize(6, FunctionalExpressionSearch.search(findClass("DumbAware")).findAll());

    assertSize(1, FunctionalExpressionSearch.search(findClass("WithDefaultMethods")).findAll());
    assertSize(1, FunctionalExpressionSearch.search(findClass("WithManyMethods")).findAll());
    assertSize(1, FunctionalExpressionSearch.search(findClass("WithManyMethods2")).findAll());
  }

  public void testArraysStreamLikeApi() {
    configure();
    assertSize(1, FunctionalExpressionSearch.search(findClass("I")).findAll());
  }

  public void testStreamOfLikeApiWithLocalVar() {
    configure();
    assertSize(1, FunctionalExpressionSearch.search(findClass("I")).findAll());
  }

  public void testStreamOfLikeApiWithField() {
    myFixture.addClass("class Base { StrType Stream = null; }");
    configure();
    assertSize(1, FunctionalExpressionSearch.search(findClass("I")).findAll());
  }

  public void testCallWithQualifiedName() {
    myFixture.addClass("package pkg.p1.p2.p3; public interface I { void run() {} }");
    myFixture.addClass("package pkg.p1.p2.p3; public class Util { public static void foo(I i) {} }");
    configure();
    assertSize(1, FunctionalExpressionSearch.search(findClass("pkg.p1.p2.p3.I")).findAll());
  }

  public void testInsideArrayInitializer() {
    myFixture.addClass("public interface Foo { void run() {}}");
    myFixture.addClass("public interface Bar { void run() {}}");
    configure();
    assertSize(3, FunctionalExpressionSearch.search(findClass("Foo")).findAll());
  }

  public void testCallOnGenericParameter() {
    configure();
    assertSize(1, FunctionalExpressionSearch.search(findClass("I")).findAll());
  }

  public void testChainStartingWithConstructor() {
    configure();
    assertSize(1, FunctionalExpressionSearch.search(findClass("IterHelper.MapIterCallback")).findAll());
  }

  public void testDontVisitInapplicableFiles() {
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

    CommonProcessors.CollectProcessor<PsiFunctionalExpression> result = new CommonProcessors.CollectProcessor<>();
    JavaFunctionalExpressionSearcher.Session session = new JavaFunctionalExpressionSearcher.Session(
      new FunctionalExpressionSearch.SearchParameters(sam, GlobalSearchScope.allScope(getProject())),
      result
    );
    session.processResults();
    assertSize(1, result.getResults());
    for (VirtualFile file : session.getFilesLookedInside()) {
      assertFalse(file.getName(), file.getName().startsWith("_"));
    }
  }

  public void testReturnedFunExpressionsDoNotHoldAst() {
    PsiClass sam = myFixture.addClass("interface I { void foo(); }");
    PsiFile usages = myFixture.addFileToProject("Some.java", "class Some { " +
                                                              "{ I[] is = { () -> {}, this::toString }; }" +
                                                              "}");
    assertFalse(((PsiFileImpl) usages).isContentsLoaded());
    assertNull(((PsiFileImpl) usages).derefStub());

    Collection<PsiFunctionalExpression> all = FunctionalExpressionSearch.search(sam).findAll();
    assertSize(2, all);
    for (PsiFunctionalExpression expression : all) {
      LeakHunter.checkLeak(expression, ASTNode.class);
    }
  }

  public void testNoAstLoadingInObviousCases() {
    PsiClass sam = myFixture.addClass("interface I { void foo(); }");
    PsiFile usages = myFixture.addFileToProject("Some.java", "class Some { " +
                                                              "void bar(I i) {}" +
                                                             "{ bar(() -> {}); }; }" +
                                                              "}");
    assertOneElement(FunctionalExpressionSearch.search(sam).findAll());
    assertFalse(((PsiFileImpl) usages).isContentsLoaded());
  }

  private PsiClass findClass(String fqName) {
    return myFixture.findClass(fqName);
  }

  private void configure() {
    myFixture.configureByFile(getTestName(false) + ".java");
  }

  public void testClassFromJdk() {
    doTestIndexSearch("(e) -> true");
  }

  public void testClassFromJdkMethodRef() {
    doTestIndexSearch("this::bar");
  }

  public void doTestIndexSearch(String expected) {
    configure();

    PsiClass predicate = findClass(Predicate.class.getName());
    final PsiFunctionalExpression next = assertOneElement(FunctionalExpressionSearch.search(predicate).findAll());
    assertEquals(expected, next.getText());
  }

  public void testConstructorReferences() {
    configure();

    myFixture.addClass("class Bar extends Foo {\n" +
                       "  public Bar() { super(() -> 1); }\n" +
                       "\n" +
                       "  {\n" +
                       "    new Foo(() -> 2) { };\n" +
                       "    new Foo(() -> 3);\n" +
                       "  }\n" +
                       "}");

    assertSize(5, FunctionalExpressionSearch.search(findClassAtCaret()).findAll());
  }

  public void testFindLambdaForAllEquivalentSams() {
    configure();

    PsiFile file = myFixture.addFileToProject("a.java",
                                              "interface Foo { void foo(); }" +
                                              "interface Foo { void bar(); } ");
    PsiClass[] fooClasses = ((PsiJavaFile)file).getClasses();
    assertOneElement(FunctionalExpressionSearch.search(fooClasses[0]).findAll());
    assertOneElement(FunctionalExpressionSearch.search(fooClasses[1]).findAll());
  }

  public void testInvalidCode() {
    configure();
    // whatever, but it shouldn't throw
    assertEmpty(FunctionalExpressionSearch.search(findClass("I")).findAll());
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
