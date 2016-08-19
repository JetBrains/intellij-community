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
import com.intellij.openapi.vfs.VirtualFile;
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
    doTestOneExpression();
  }

  public void testMethodArgumentByTypeParameter() throws Exception {
    doTestOneExpression();
  }

  public void testFieldDeclaredInFileWithoutFunctionalInterfaces() throws Exception {
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

  public void testVarargPosition() throws Exception {
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

  public void testFieldFromAnonymousClassScope() throws Exception {
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

  public void testCallOnGenericParameter() {
    configure();
    assertSize(1, FunctionalExpressionSearch.search(findClass("I")).findAll());
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

    assertSize(1, FunctionalExpressionSearch.search(sam).findAll());
    for (VirtualFile file : JavaFunctionalExpressionSearcher.getFilesToSearchInPsi(sam)) {
      assertFalse(file.getName(), file.getName().startsWith("_"));
    }
  }

  private PsiClass findClass(String i) {
    return JavaPsiFacade.getInstance(getProject()).findClass(i, GlobalSearchScope.allScope(getProject()));
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
    assert predicate != null;
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
