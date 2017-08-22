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
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.List;

/**
 * @author mike
 */
public class ExceptionCheckingTest extends LightCodeInsightTestCase {
  public void testNoExceptions() {
    PsiMethodCallExpression methodCall = createCall("void foo() { System.out.println(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testCheckedUnhandledException() {
    PsiMethodCallExpression methodCall = createCall("void foo() { throwsIOException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertEquals(1, exceptions.size());
    assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
  }

  public void testCheckedDeclaredException() {
    PsiMethodCallExpression methodCall = createCall("void foo() throws java.io.IOException { throwsIOException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testCheckedDeclaredAncestorException() {
    PsiMethodCallExpression methodCall = createCall("void foo() throws Exception { throwsIOException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testCheckedDeclaredAnotherException() {
    PsiMethodCallExpression methodCall = createCall("void foo() throws IllegalAccessException { throwsIOException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertEquals(1, exceptions.size());
    assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
  }

  public void testCheckedCatchedException() {
    PsiMethodCallExpression methodCall = createCall("void foo() { try { throwsIOException(); } catch (java.io.IOException e) {} }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testCheckedLikeCatchedException() {
    PsiMethodCallExpression methodCall = createCall("void foo() { try { } catch (java.io.IOException e) {throwsIOException();} }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertEquals(1, exceptions.size());
    assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
  }

  public void testCheckedCatchedAncestorException() {
    PsiMethodCallExpression methodCall = createCall("void foo() { try { throwsIOException(); } catch (Exception e) {} }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testCheckedCatchedAnotherException() {
    PsiMethodCallExpression methodCall = createCall("void foo() { try { throwsIOException(); } catch (IllegalAccessException e) {} }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertEquals(1, exceptions.size());
    assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
  }

  public void testRuntimeException() {
    PsiMethodCallExpression methodCall = createCall("void foo() { throwsRuntimeException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testError() {
    PsiMethodCallExpression methodCall = createCall("void foo() { throwsError(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testArray() {
    PsiMethodCallExpression methodCall = createCall("void foo() { int[] arr; arr.clone(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testConstructor1() {
    final PsiNewExpression newExpression = createNewExpression("void foo() { new ClassIOException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(newExpression, null);
    assertEquals(1, exceptions.size());
    assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
  }

  public void testCollectExceptionsInTryCatch() {
    PsiMethodCallExpression methodCall = createCall("void foo() { try { throwsIOException(); } catch (java.io.Exception e) {} }");
    PsiTryStatement statement = PsiTreeUtil.getParentOfType(methodCall, PsiTryStatement.class);
    final Collection<PsiClassType> exceptions = ExceptionUtil.collectUnhandledExceptions(statement.getTryBlock(), statement.getTryBlock());
    assertEquals(1, exceptions.size());
    assertEquals("java.io.IOException", exceptions.iterator().next().getCanonicalText());
  }


  private static PsiMethodCallExpression createCall(@NonNls final String body) {
    final PsiFile file = createFile("test.java", "class Test { " + body +
      "void throwsIOException() throws java.io.IOException {}" +
      "void throwsRuntimeException() throws RuntimeException {}" +
      "void throwsError() throws Error {}" +
      "}");
    PsiMethodCallExpression methodCall = findMethodCall(file);
    assertNotNull(methodCall);
    return methodCall;
  }

  private static PsiNewExpression createNewExpression(@NonNls final String body) {
    final PsiFile file = createFile("test.java", "class Test { " + body +
      "class ClassIOException { ClassIOException() throws java.io.IOException {} }" +
      "class ClassError { ClassError() throws Error {} }" +
      "class ClassRuntime { ClassRuntime() throws RuntimeException {} }" +
      "}");

    PsiNewExpression newExpression = findNewExpression(file);
    assertNotNull(newExpression);
    return newExpression;
  }

  private static PsiNewExpression findNewExpression(PsiElement element) {
    if (element instanceof PsiNewExpression) {
      return (PsiNewExpression)element;
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      final PsiNewExpression expression = findNewExpression(child);
      if (expression != null) return expression;
    }

    return null;
  }

  private static PsiMethodCallExpression findMethodCall(PsiElement element) {
    if (element instanceof PsiMethodCallExpression) {
      return (PsiMethodCallExpression)element;
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      final PsiMethodCallExpression call = findMethodCall(child);
      if (call != null) return call;
    }

    return null;
  }
}
