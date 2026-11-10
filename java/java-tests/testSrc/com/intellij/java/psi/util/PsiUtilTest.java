// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.JBIterable;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Iterator;

import static com.intellij.psi.util.PsiUtil.hasDefaultConstructor;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class PsiUtilTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testTypeParameterIterator() {
    ReadAction.runBlocking(() -> {
      PsiClass classA = createClass("class A<T> {}");
      Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(classA);
      compareIterator(iterator, "T");
    });
  }

  @Test
  public void testTypeParameterIterator1() {
    ReadAction.runBlocking(() -> {
      PsiClass classA = createClass("class A<T> { class B<X> {} }");
      Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(classA.getInnerClasses()[0]);
      compareIterator(iterator, "X", "T");
    });
  }

  @Test
  public void testTypeParameterIterator2() {
    ReadAction.runBlocking(() -> {
      PsiClass classA = createClass("class A<T> { static class B<X> {} }");
      Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(classA.getInnerClasses()[0]);
      compareIterator(iterator, "X");
    });
  }

  @Test
  public void testTypeParameterIterator3() {
    ReadAction.runBlocking(() -> {
      PsiClass classA = createClass("class A<T> { class B<X, Y> {} }");
      Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(classA.getInnerClasses()[0]);
      compareIterator(iterator, "Y", "X", "T");
    });
  }

  @Test
  public void testTopLevelClass() {
    PsiClass outer = createTopLevelClass("class Outer { class Inner {} }");
    ReadAction.runBlocking(() -> {
      assertSame(outer, PsiUtil.getTopLevelClass(outer));
      PsiClass inner = outer.getInnerClasses()[0];
      assertSame(outer, PsiUtil.getTopLevelClass(inner));
    });
  }

  @Test
  public void testPackageName() {
    PsiClass outer = createTopLevelClass("package pkg;\nclass Outer { class Inner {} }");
    ReadAction.runBlocking(() -> {
      assertEquals("pkg", PsiUtil.getPackageName(outer));
      PsiClass inner = outer.getInnerClasses()[0];
      assertEquals("pkg", PsiUtil.getPackageName(inner));
    });
  }

  @Test
  public void testHasDefaultConstructor() {
    assertTrue(hasDefaultConstructorOf("public class A {}"));
    assertTrue(hasDefaultConstructorOf("public class A { public A() {}}"));
    assertFalse(hasDefaultConstructorOf("public class A { public A(int i) {}}"));
    assertTrue(hasDefaultConstructorOf("public class A extends B { } class B {}"));
    assertTrue(hasDefaultConstructorOf("public class A extends B { } class B { B(int i) {}}"));

    assertFalse(hasDefaultConstructorOf("class A { A() {}}"));
    assertTrue(hasDefaultConstructorOf("class A { A() {}}", false, true));
    assertTrue(hasDefaultConstructorOf("class A { A() {}}", true, true));
    assertFalse(hasDefaultConstructorOf("class A { A() {}}", true, false));
    assertTrue(hasDefaultConstructorOf("class A { protected A() {}}", true, false));
  }

  private boolean hasDefaultConstructorOf(String text) {
    PsiClass aClass = createTopLevelClass(text);
    return ReadAction.computeBlocking(() -> hasDefaultConstructor(aClass));
  }

  private boolean hasDefaultConstructorOf(String text, boolean allowProtected, boolean allowPrivateAndPackagePrivate) {
    PsiClass aClass = createTopLevelClass(text);
    return ReadAction.computeBlocking(() -> hasDefaultConstructor(aClass, allowProtected, allowPrivateAndPackagePrivate));
  }

  private PsiClass createTopLevelClass(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("A.java", text);
    return ReadAction.computeBlocking(() -> ((PsiJavaFile)file).getClasses()[0]);
  }

  private PsiClass createClass(@NotNull @Language("JAVA") String text) throws IncorrectOperationException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createClassFromText(text, null).getInnerClasses()[0]);
  }

  private static void compareIterator(Iterator<PsiTypeParameter> it, String... expected) {
    assertEquals(Arrays.asList(expected), JBIterable.once(it).map(PsiTypeParameter::getName).toList());
  }
}