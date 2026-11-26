// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ExceptionCheckingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testNoExceptions() {
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression methodCall = createCall("void foo() { System.out.println(); }");
      List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
      assertTrue(exceptions.isEmpty());
    });
  }

  @Test
  public void testCheckedUnhandledException() {
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression methodCall = createCall("void foo() { throwsIOException(); }");
      List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
      assertEquals(1, exceptions.size());
      assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
    });
  }

  @Test
  public void testCheckedDeclaredException() {
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression methodCall = createCall("void foo() throws java.io.IOException { throwsIOException(); }");
      List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
      assertTrue(exceptions.isEmpty());
    });
  }

  @Test
  public void testCheckedDeclaredAncestorException() {
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression methodCall = createCall("void foo() throws Exception { throwsIOException(); }");
      List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
      assertTrue(exceptions.isEmpty());
    });
  }

  @Test
  public void testCheckedDeclaredAnotherException() {
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression methodCall = createCall("void foo() throws IllegalAccessException { throwsIOException(); }");
      List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
      assertEquals(1, exceptions.size());
      assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
    });
  }

  @Test
  public void testCheckedCatchedException() {
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression methodCall = createCall("void foo() { try { throwsIOException(); } catch (java.io.IOException e) {} }");
      List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
      assertTrue(exceptions.isEmpty());
    });
  }

  @Test
  public void testCheckedLikeCatchedException() {
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression methodCall = createCall("void foo() { try { } catch (java.io.IOException e) {throwsIOException();} }");
      List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
      assertEquals(1, exceptions.size());
      assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
    });
  }

  @Test
  public void testCheckedCatchedAncestorException() {
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression methodCall = createCall("void foo() { try { throwsIOException(); } catch (Exception e) {} }");
      List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
      assertTrue(exceptions.isEmpty());
    });
  }

  @Test
  public void testCheckedCatchedAnotherException() {
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression methodCall = createCall("void foo() { try { throwsIOException(); } catch (IllegalAccessException e) {} }");
      List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
      assertEquals(1, exceptions.size());
      assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
    });
  }

  @Test
  public void testRuntimeException() {
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression methodCall = createCall("void foo() { throwsRuntimeException(); }");
      List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
      assertTrue(exceptions.isEmpty());
    });
  }

  @Test
  public void testError() {
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression methodCall = createCall("void foo() { throwsError(); }");
      List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
      assertTrue(exceptions.isEmpty());
    });
  }

  @Test
  public void testArray() {
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression methodCall = createCall("void foo() { int[] arr; arr.clone(); }");
      List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
      assertTrue(exceptions.isEmpty());
    });
  }

  @Test
  public void testConstructor1() {
    ReadAction.runBlocking(() -> {
      final PsiNewExpression newExpression = createNewExpression("void foo() { new ClassIOException(); }");
      List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(newExpression, null);
      assertEquals(1, exceptions.size());
      assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
    });
  }

  @Test
  public void testCollectExceptionsInTryCatch() {
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression methodCall = createCall("void foo() { try { throwsIOException(); } catch (java.io.Exception e) {} }");
      PsiTryStatement statement = PsiTreeUtil.getParentOfType(methodCall, PsiTryStatement.class);
      final Collection<PsiClassType> exceptions = ExceptionUtil.collectUnhandledExceptions(statement.getTryBlock(), statement.getTryBlock());
      assertEquals(1, exceptions.size());
      assertEquals("java.io.IOException", exceptions.iterator().next().getCanonicalText());
    });
  }

  private PsiMethodCallExpression createCall(@NonNls final String body) {
    final PsiFile file = PsiFileFactory.getInstance(codeInsightFixture.get().getProject()).createFileFromText(
      "test.java", JavaFileType.INSTANCE, "class Test { " + body +
      "void throwsIOException() throws java.io.IOException {}" +
      "void throwsRuntimeException() throws RuntimeException {}" +
      "void throwsError() throws Error {}" +
      "}", LocalTimeCounter.currentTime(), true);
    PsiMethodCallExpression methodCall = findMethodCall(file);
    assertNotNull(methodCall);
    return methodCall;
  }

  private PsiNewExpression createNewExpression(@NonNls final String body) {
    final PsiFile file = PsiFileFactory.getInstance(codeInsightFixture.get().getProject()).createFileFromText(
      "test.java", JavaFileType.INSTANCE, "class Test { " + body +
      "class ClassIOException { ClassIOException() throws java.io.IOException {} }" +
      "class ClassError { ClassError() throws Error {} }" +
      "class ClassRuntime { ClassRuntime() throws RuntimeException {} }" +
      "}", LocalTimeCounter.currentTime(), true);

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
