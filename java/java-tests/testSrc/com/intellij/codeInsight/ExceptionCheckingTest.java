package com.intellij.codeInsight;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

import java.util.List;
import java.util.Collection;

/**
 * @author mike
 */
public class ExceptionCheckingTest extends LightCodeInsightTestCase {
  public void testNoExceptions() throws Exception {
    PsiMethodCallExpression methodCall = createCall("void foo() { System.out.println(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testCheckedUnhandledException() throws Exception {
    PsiMethodCallExpression methodCall = createCall("void foo() { throwsIOException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertEquals(1, exceptions.size());
    assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
  }

  public void testCheckedDeclaredException() throws Exception {
    PsiMethodCallExpression methodCall = createCall("void foo() throws java.io.IOException { throwsIOException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testCheckedDeclaredAncestorException() throws Exception {
    PsiMethodCallExpression methodCall = createCall("void foo() throws Exception { throwsIOException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testCheckedDeclaredAnotherException() throws Exception {
    PsiMethodCallExpression methodCall = createCall("void foo() throws IllegalAccessException { throwsIOException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertEquals(1, exceptions.size());
    assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
  }

  public void testCheckedCatchedException() throws Exception {
    PsiMethodCallExpression methodCall = createCall("void foo() { try { throwsIOException(); } catch (java.io.IOException e) {} }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testCheckedLikeCatchedException() throws Exception {
    PsiMethodCallExpression methodCall = createCall("void foo() { try { } catch (java.io.IOException e) {throwsIOException();} }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertEquals(1, exceptions.size());
    assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
  }

  public void testCheckedCatchedAncestorException() throws Exception {
    PsiMethodCallExpression methodCall = createCall("void foo() { try { throwsIOException(); } catch (Exception e) {} }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testCheckedCatchedAnotherException() throws Exception {
    PsiMethodCallExpression methodCall = createCall("void foo() { try { throwsIOException(); } catch (IllegalAccessException e) {} }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertEquals(1, exceptions.size());
    assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
  }

  public void testRuntimeException() throws Exception {
    PsiMethodCallExpression methodCall = createCall("void foo() { throwsRuntimeException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testError() throws Exception {
    PsiMethodCallExpression methodCall = createCall("void foo() { throwsError(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testArray() throws Exception {
    PsiMethodCallExpression methodCall = createCall("void foo() { int[] arr; arr.clone(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testConstructor1() throws Exception {
    final PsiNewExpression newExpression = createNewExpression("void foo() { new ClassIOException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(newExpression, null);
    assertEquals(1, exceptions.size());
    assertEquals("java.io.IOException", exceptions.get(0).getCanonicalText());
  }

  public void testCollectExceptionsInTryCatch() throws Exception {
    PsiMethodCallExpression methodCall = createCall("void foo() { try { throwsIOException(); } catch (java.io.Exception e) {} }");
    PsiTryStatement statement = PsiTreeUtil.getParentOfType(methodCall, PsiTryStatement.class);
    final Collection<PsiClassType> exceptions = ExceptionUtil.collectUnhandledExceptions(statement.getTryBlock(), statement.getTryBlock());
    assertEquals(1, exceptions.size());
    assertEquals("java.io.IOException", exceptions.iterator().next().getCanonicalText());
  }


  private static PsiMethodCallExpression createCall(@NonNls final String body) throws Exception {
    final PsiFile file = createFile("test.java", "class Test { " + body +
      "void throwsIOException() throws java.io.IOException {}" +
      "void throwsRuntimeException() throws RuntimeException {}" +
      "void throwsError() throws Error {}" +
      "}");
    PsiMethodCallExpression methodCall = findMethodCall(file);
    assertNotNull(methodCall);
    return methodCall;
  }

  private static PsiNewExpression createNewExpression(@NonNls final String body) throws Exception {
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
