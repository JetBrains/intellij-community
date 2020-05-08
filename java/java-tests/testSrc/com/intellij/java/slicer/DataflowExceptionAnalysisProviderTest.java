// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.slicer;

import com.intellij.execution.filters.ExceptionAnalysisProvider;
import com.intellij.execution.filters.ExceptionInfo;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataflowExceptionAnalysisProviderTest extends LightJavaCodeInsightTestCase {
  public void testArrayIndex() {
    doTest("java.lang.ArrayIndexOutOfBoundsException: 10",
           "Find why 'idx' could be 10", "class X {static int test(int[] x, int idx) {return x<caret>[idx];}}");
  }

  public void testClassCast() {
    doTest("java.lang.ClassCastException: class X cannot be cast to class java.lang.Number",
           "Find why 'obj' could be instanceof X (not-null)", 
           "class X {static void test(Object obj) {System.out.println((<caret>(Number) obj).intValue());}}");
  }
  
  public void testClassCastUnknownClass() {
    doTest("java.lang.ClassCastException: class XYZ cannot be cast to class java.lang.Number",
           "Find why 'obj' could be not instanceof java.lang.Number (not-null)", 
           "class X {static void test(Object obj) {System.out.println((<caret>(Number) obj).intValue());}}");
  }
  
  public void testNpe() {
    doTest("java.lang.NullPointerException: Cannot invoke \"Object.hashCode()\" because \"obj\" is null",
           "Find why 'obj' could be null",
           "class X {static void test(Object obj) {System.out.println(obj.<caret>hashCode());}}");
  }
  
  public void testNpeArray() {
    doTest("java.lang.NullPointerException: Cannot load from object array because \"obj\" is null",
           "Find why 'obj' could be null",
           "class X {static void test(Object[] obj) {System.out.println(obj<caret>[0]);}}");
  }
  
  public void testNpeSynchronized() {
    doTest("java.lang.NullPointerException: Cannot enter synchronized block because \"obj\" is null",
           "Find why 'obj' could be null",
           "class X {static void test(Object obj) {<caret>synchronized(obj){}}}");
  }
  
  public void testNpeThrow() {
    doTest("java.lang.NullPointerException: Cannot throw exception because \"obj\" is null",
           "Find why 'obj' could be null", 
           "class X {static void test(RuntimeException obj) {<caret>throw obj;}}");
  }
  
  public void testAssertChar() {
    doTest("java.lang.AssertionError",
           "Find why 'c' could be 97",
           "class X {static void test(char c) {<caret>assert c != 'a';}}");
  }
  
  public void testAssertDivisibility() {
    doTest("java.lang.AssertionError",
           "Find why 'i' could be odd",
           "class X {static void test(int i) {<caret>assert i % 2 == 0;}}");
  }

  public void testAssertAnd() {
    doTest("java.lang.AssertionError",
           "Find why 'idx' could be <= 0 or >= 4",
           "class X {static void test(int idx) {<caret>assert idx > 0 && idx < 4;}}");
  }

  public void testAssertOr() {
    doTest("java.lang.AssertionError",
           "Find why 'idx' could be in {0..4}",
           "class X {static void test(int idx) {<caret>assert idx < 0 || idx > 4;}}");
  }

  public void testAssertByte() {
    doTest("java.lang.AssertionError",
           "Find why 'idx' could be <= 0",
           "class X {static void test(byte idx) {<caret>assert idx > 0;}}");
  }

  public void testAssertShort() {
    doTest("java.lang.AssertionError",
           "Find why 'idx' could be <= 0",
           "class X {static void test(short idx) {<caret>assert idx > 0;}}");
  }

  public void testAssertLong() {
    doTest("java.lang.AssertionError",
           "Find why 'idx' could be <= 0",
           "class X {static void test(long idx) {<caret>assert idx > 0;}}");
  }

  public void testAssertBoxed() {
    // boxed types are unsupported
    doTest("java.lang.AssertionError",
           null,
           "class X {static void test(Long idx) {<caret>assert idx > 0;}}");
  }

  public void testInstanceOf() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'obj' could be null or not instanceof java.lang.String",
           "class X {static void test(Object obj) {if (!(obj instanceof String)) throw <caret>new IllegalArgumentException();}}");
  }
  
  public void testStringInEquality() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 's' could be != \"hello\" (not-null)",
           "class X {static void test(String s) {if (!s.equals(\"hello\")) throw <caret>new IllegalArgumentException();}}");
  }

  public void testStringEqEq() {
    doTest("java.lang.IllegalArgumentException",
           null,
           "class X {static void test(String s) {if (s == \"hello\")) throw <caret>new IllegalArgumentException();}}");
  }

  public void testClassEquality() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'cls' could be String",
           "class X {static void test(Class<?> cls) {if (cls.equals(String.class)) throw <caret>new IllegalArgumentException();}}");
  }

  public void testClassInEquality() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'cls' could be != String (not-null)",
           "class X {static void test(Class<?> cls) {if (!cls.equals(String.class)) throw <caret>new IllegalArgumentException();}}");
  }

  public void testClassInEqualityInverted() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'cls' could be null or != String",
           "class X {static void test(Class<?> cls) {if (!String.class.equals(cls)) {throw <caret>new IllegalArgumentException();}}}");
  }

  public void testBooleanTrue() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'b' could be true",
           "class X {static void test(boolean b) {if (b) {throw <caret>new IllegalArgumentException();}}}");
  }

  public void testBooleanFalse() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'b' could be false",
           "class X {static void test(boolean b) {if (!b) {throw <caret>new IllegalArgumentException();}}}");
  }

  public void testIsNull() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'obj' could be null",
           "class X {static void test(Object obj) {if (obj == null) {throw <caret>new IllegalArgumentException();}}}");
  }

  public void testIsNotNull() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'obj' could be not-null",
           "class X {static void test(Object obj) {if (null != obj) {throw <caret>new IllegalArgumentException();}}}");
  }

  public void testEnumEquality() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'x' could be X.A",
           "enum X {A,B,C;static void test(X x) {if (x == X.A) throw <caret>new IllegalArgumentException();}}");
  }
  
  public void testInSwitch() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'x' could be 5",
           "class X {" +
           "  static void test(int x) {\n" +
           "    switch (x) {\n" +
           "      case 3:\n" +
           "        System.out.println(\"oops\");\n" +
           "        break;\n" +
           "      case 5:\n" +
           "        throw <caret>new IllegalArgumentException();\n" +
           "    }\n" +
           "  }" +
           "}");
  }

  public void testInSwitchRule() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'x' could be in {2, 3, 5}",
           "class X {static void test(int x) { switch (x) { case 2,3,5 -> throw <caret>new RuntimeException();default -> {} } }}");
  }

  public void testInSwitchDefault() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'x' could be <= 0 or >= 5",
           "class X {static void test(int x) { switch (x) { " +
           "case 1: break; case 2: break; case 3, 4: break; " +
           "case 5: default: throw <caret>new IllegalArgumentException(); } }");
  }

  public void testInSwitchDefaultString() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 's' could be != \"BAR\", \"FOO\" (not-null)",
           "class X {static void test(String s) { switch (s) { " +
           "case \"FOO\": break; case \"BAR\": break;" +
           "default: case \"BAZ\": throw <caret>new IllegalArgumentException(); } }");
  }
  
  public void testIfExits() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'x' could be >= 0",
           "class X {" +
           "  static void test(int x) {\n" +
           "    if (x < 0) {\n" +
           "      System.out.println(\"ok\");\n" +
           "      return;\n" +
           "    }\n" +
           "    throw <caret>new IllegalArgumentException();\n" +
           "  }" +
           "}");
  }
  
  public void testNoInfo() {
    doTest("java.lang.IllegalArgumentException",
           null,
           "class X {static void test(X x) {throw <caret>new IllegalArgumentException();}}");
  }
  
  public void testNegativeArraySizeException() {
    doTest("java.lang.NegativeArraySizeException: -2",
           "Find why 'x' could be -2",
           "class X {static void test(int x) {int[] data = <caret>new int[x];}}");
  }
  
  public void testDivisionByZero() {
    doTest("java.lang.ArithmeticException: / by zero",
           "Find why 'y' could be 0",
           "class X {static void test(int x, int y) {int[] data = new int[x<caret>/y];}}");
  }
  
  public void testModByZero() {
    doTest("java.lang.ArithmeticException: / by zero",
           "Find why 'y' could be 0",
           "class X {static void test(int x, long y) {long res = x <caret>% y;}}");
  }
  
  public void testRequireNonNull() {
    doTest(null, "Find why 'str' could be null",
           "class X {static void test(String str, String msg) {java.util.Objects.<caret>requireNonNull(str, msg);}}");
  }

  public void testAssertNull() {
    doTest(null, "Find why 'str' could be not-null",
           "class X {static void test(String str) {<caret>assertNull(str);}" +
           "static void assertNull(Object obj) {if(obj != null) throw new AssertionError();}}");
  }

  public void testAssertTrue() {
    doTest(null, "Find why 'x' could be <= 0",
           "class X {static void test(int x) {<caret>assertTrue(x > 0);}" +
           "static void assertTrue(boolean flag) {if(!flag) throw new AssertionError();}}");
  }

  public void testAssertFalse() {
    doTest(null, "Find why 'x' could be >= 1",
           "class X {static void test(int x) {<caret>assertFalse(x > 0);}" +
           "static void assertFalse(boolean flag) {if(flag) throw new AssertionError();}}");
  }

  public void testOptionalGet() {
    // Not supported
    doTest(null, null,
           "class X {static void test(java.util.Optional x) {x.<caret>get();}}");
  }

  private void doTest(@Nullable("If intermediate line action should be tested") String exceptionLine,
                      @Nullable("If no action is expected") String expectedActionTitle,
                      @NotNull String source) {
    configureFromFileText("Test.java", source);
    int offset = getEditor().getCaretModel().getOffset();
    assertTrue("Offset is not set", offset > 0);
    PsiElement leaf = getFile().findElementAt(offset);
    AnAction action;
    ExceptionAnalysisProvider analysisProvider = getProject().getService(ExceptionAnalysisProvider.class);
    if (exceptionLine != null) {
      ExceptionInfo info = ExceptionInfo.parseMessage(exceptionLine, 0);
      assertNotNull(info);
      action = analysisProvider.getAnalysisAction(leaf, info);
    } else {
      action = analysisProvider.getIntermediateRowAnalysisAction(leaf);
    }
    if (expectedActionTitle == null) {
      assertNull(action);
    } else {
      assertNotNull(action);
      String text = action.getTemplatePresentation().getDescription();
      assertEquals(expectedActionTitle, text);
    }
  }
}
