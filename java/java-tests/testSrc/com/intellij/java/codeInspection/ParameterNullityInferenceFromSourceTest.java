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
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import one.util.streamex.StreamEx;

public class ParameterNullityInferenceFromSourceTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSimpleDereference() {
    assertNullity("+", "String test(String s) { return s.trim(); }");
  }

  public void testReassign() {
    assertNullity("-+", "String test(String s, String s1) { s+=s1.trim();return s.trim(); }");
  }

  public void testNoDereference() {
    assertNullity("--", "String test(String s, int x) { return s == null ? null : s.trim(); }");
  }

  public void testSwitchPrimitive() {
    assertNullity("-", "String test(int x) { switch(x) {default:return null;} }");
  }

  public void testSwitchObject() {
    assertNullity("+", "String test(Integer x) { switch(x) {default:return null;} }");
  }

  public void testSynchronized() {
    assertNullity("++", "void test(Object lock, String s) {synchronized(lock) {;;System.out.println(s.trim());}}");
  }

  public void testArray() {
    assertNullity("++", "String test(int[] arr, int[] arr2) { System.out.println(arr2[0]);for(int a: arr) {} }");
  }

  public void testForInfiniteIf() {
    assertNullity("+-+",
                  "String test(String s, String s1, String s2) { for(;;) if(s.length()+s2.length() > 0) return s1.trim(); }");
  }

  public void testForFinite() {
    assertNullity("++",
                  "void test(Node n, Tree t) { for(Node x=n.firstChild();x!=t.getEnd();x=x.next()) System.out.println(x); }");
  }

  public void testForDerefAfterCondition() {
    assertNullity("-",
                  "void test(Node n) { for(;n != null;n=n.next()) System.out.println(n); }");
  }

  public void testForFiniteIf() {
    assertNullity("-+-",
                  "String test(String s, String s1, String s2) { for(;s1.length()>0;) if(s.length()+s2.length() > 0) return s1.trim(); }");
  }

  public void testWhileTrue() {
    assertNullity("+",
                  "String test(String s) { while(true) System.out.println(s.trim()); }");
  }

  public void testWhileOther() {
    assertNullity("-",
                  "String test(String s) { while(s != null) System.out.println(s.trim()); }");
  }

  public void testThrow() {
    assertNullity("+",
                  "String test(Object obj) { throw new RuntimeException(obj.toString()); }");
  }

  public void testThrowOk() {
    assertNullity("-",
                  "String test(Object obj) { throw new RuntimeException(String.valueOf(obj)); }");
  }

  public void testLambda() {
    assertNullity("-",
                  "String test(Object obj) { Runnable r = () -> obj.hashCode(); if(obj != null) r.run(); }");
  }

  public void testMethodRef() {
    assertNullity("+",
                  "String test(Object obj) { Runnable r = obj::hashCode; if(obj != null) r.run(); }");
  }

  public void testClass() {
    assertNullity("-+",
                  "String test(Object obj, String s) { class X { int i = obj.hashCode(); }\n" +
                  "if(!s.isEmpty() && obj != null) System.out.println(new X().i); }");
  }

  public void testAnonymousClass() {
    assertNullity("+-",
                  "String test(String s, String s1) { " +
                  "Object x = new Object(){" +
                  "String s1 = s.trim();" +
                  "{System.out.println(s1.trim());}}" +
                  "}");
  }

  public void testTryNPECaught() {
    assertNullity("-",
                  "String test(String s) {" +
                  "try {System.out.println(s.trim());}" +
                  "catch(RuntimeException ex) {}" +
                  "}");
  }

  public void testTryNPECaughtMultiCatch() {
    assertNullity("-",
                  "String test(String s) {" +
                  "try {System.out.println(s.trim());}" +
                  "catch(InternalError | NullPointerException ex) {}" +
                  "}");
  }

  public void testTryNPENotCaught() {
    assertNullity("+",
                  "String test(String s) {" +
                  "try {System.out.println(s.trim());}" +
                  "catch(InternalError ex) {}" +
                  "}");
  }

  public void testTryWithResources() {
    assertNullity("+",
                  "String test(String path) {" +
                  "try(java.io.FileReader fr = new java.io.FileReader(path.trim())) {System.out.println(fr.read());}" +
                  "catch(java.io.IOException ex) {}" +
                  "}");
  }

  public void testSwitchWithPattern() {
    assertNullity("-",
                  """
                    void test(String r) {
                            switch (r) {
                                case null:
                                    break;
                            }
                        }""");
  }

  public void testSwitchExpression() {
    assertNullity("+",
                  "int test(String s) { return switch(s) {case \"x\"->1;default ->2;}} ");
  }

  public void testUseConfiguredNullityAnnotation() {
    PsiClass clazz = myFixture.addClass("final class Foo { void foo(String s) { s.hashCode(); } }");
    PsiParameter parameter = clazz.getMethods()[0].getParameterList().getParameters()[0];
    NullableNotNullManager manager = NullableNotNullManager.getInstance(parameter.getProject());
    String javax = "javax.annotation.Nonnull";
    manager.setDefaultNotNull(javax);
    try {
      assertEquals(javax, manager.findOwnNullabilityInfo(parameter).getAnnotation().getQualifiedName());
    } finally {
      manager.setDefaultNotNull(AnnotationUtil.NOT_NULL);
    }
  }

  // expected: + = notnull, - = unknown/nullable for each parameter
  private void assertNullity(String expected, String classBody) {
    PsiClass clazz = myFixture.addClass("final class Foo { " + classBody + " }");
    assertFalse(((PsiFileImpl)clazz.getContainingFile()).isContentsLoaded());
    PsiMethodImpl method = (PsiMethodImpl)clazz.getMethods()[0];
    String actual = StreamEx.of(method.getParameterList().getParameters())
      .map(JavaSourceInference::inferNullability)
      .map(n -> n == Nullability.NOT_NULL ? "+" : "-")
      .joining();
    assertFalse(((PsiFileImpl)clazz.getContainingFile()).isContentsLoaded());
    assertEquals(expected, actual);
  }
}
