/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.psi.util;

import com.intellij.JavaTestUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.ClassUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;

/**
 * @author yole
 */
public class ClassUtilTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/psi/classUtil/";
  }

  public void testFindPsiClassByJvmName() {
    myFixture.configureByFile("ManyClasses.java");

    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$1"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$1$1"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$1FooLocal"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$1FooLocal$1"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Child"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Child$"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Ma$ked"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Ma$ked$Ne$ted"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Edge"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Edge$"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Edge$$$tu_pid_ne$s"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "Local"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "Local$Sub"));

    PsiClass local = ClassUtil.findPsiClassByJVMName(getPsiManager(), "Local$");
    assertNotNull(local);
    assertEquals("Local$", local.getName());

    PsiClass sub = ClassUtil.findPsiClassByJVMName(getPsiManager(), "Local$$Sub");
    assertNotNull(sub);
    assertEquals("Local$", ((PsiClass)sub.getParent()).getName());

    PsiClass fooLocal2 = ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$2FooLocal");
    assertNotNull(fooLocal2);
    assertEquals("Runnable", fooLocal2.getImplementsListTypes()[0].getClassName());
  }

  private PsiClass createClass(String text) throws IncorrectOperationException {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory().createClassFromText(text, null).getInnerClasses()[0];
  }

  private String signature(String method) {
    return signature(method, "");
  }

  private String signature(String method, String typeParam) {
    return ClassUtil.getAsmMethodSignature(
      createClass("class Clazz" + typeParam + " { " + method + " }").getMethods()[0]
    );
  }

  public void testAsmMethodSignature() {
    assertEquals( "(I)V", signature("void m(int i) {}"));
    assertEquals( "(J)V", signature("void m(long i) {}"));
    assertEquals( "(B)V", signature("void m(byte i) {}"));
    assertEquals( "(Z)V", signature("void m(boolean i) {}"));
    assertEquals( "(D)V", signature("void m(double i) {}"));
    assertEquals( "(F)V", signature("void m(float i) {}"));
    assertEquals( "(C)V", signature("void m(char i) {}"));
    assertEquals( "(S)V", signature("void m(short i) {}"));
    assertEquals( "(SI)V", signature("void m(short s, int i) {}"));
    assertEquals( "([S[I)V", signature("void m(short[] s, int[] i) {}"));
    assertEquals( "(Ljava/lang/Object;)V", signature("void m(T t) {}", "<T>"));
    assertEquals( "(Ljava/lang/String;)V", signature("void m(T t) {}", "<T extends String>"));
    assertEquals( "(Ljava/lang/String;)V", signature("<T extends String> void m(T t) {}"));
    assertEquals( "([Ljava/lang/Object;)V", signature("void m(T[] t) {}", "<T>"));
    assertEquals( "(Ljava/lang/Object;Ljava/lang/String;)V", signature("void m(T t, String s) {}", "<T>"));
    assertEquals( "(Ljava/lang/String;)Ljava/lang/String;", signature("String m(String s) { return \"str\"; }"));
  }
}
