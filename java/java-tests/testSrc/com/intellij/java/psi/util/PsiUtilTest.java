// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.util;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.JBIterable;

import java.util.Arrays;
import java.util.Iterator;

/**
 *  @author dsl
 */
public class PsiUtilTest extends LightCodeInsightFixtureTestCase {
  public void testTypeParameterIterator() {
    PsiClass classA = createClass("class A<T> {}");
    Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(classA);
    compareIterator(iterator, "T");
  }

  public void testTypeParameterIterator1() {
    PsiClass classA = createClass("class A<T> { class B<X> {} }");
    Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(classA.getInnerClasses()[0]);
    compareIterator(iterator, "X", "T");
  }

  public void testTypeParameterIterator2() {
    PsiClass classA = createClass("class A<T> { static class B<X> {} }");
    Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(classA.getInnerClasses()[0]);
    compareIterator(iterator, "X");
  }

  public void testTypeParameterIterator3() {
    PsiClass classA = createClass("class A<T> { class B<X, Y> {} }");
    Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(classA.getInnerClasses()[0]);
    compareIterator(iterator, "Y", "X", "T");
  }

  public void testTopLevelClass() {
    PsiClass outer = ((PsiJavaFile)myFixture.configureByText("a.java", "class Outer { class Inner {} }")).getClasses()[0];
    assertSame(outer, PsiUtil.getTopLevelClass(outer));
    PsiClass inner = outer.getInnerClasses()[0];
    assertSame(outer, PsiUtil.getTopLevelClass(inner));
  }

  public void testPackageName() {
    PsiClass outer = ((PsiJavaFile)myFixture.configureByText("a.java", "package pkg;\nclass Outer { class Inner {} }")).getClasses()[0];
    assertEquals("pkg", PsiUtil.getPackageName(outer));
    PsiClass inner = outer.getInnerClasses()[0];
    assertEquals("pkg", PsiUtil.getPackageName(inner));
  }


  private PsiClass createClass(String text) throws IncorrectOperationException {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory().createClassFromText(text, null).getInnerClasses()[0];
  }

  private static void compareIterator(Iterator<PsiTypeParameter> it, String... expected) {
    assertEquals(Arrays.asList(expected), JBIterable.once(it).map(PsiTypeParameter::getName).toList());
  }
}