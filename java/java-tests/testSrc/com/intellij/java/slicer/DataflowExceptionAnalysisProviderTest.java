// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.slicer;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.execution.filters.ExceptionAnalysisProvider;
import com.intellij.execution.filters.ExceptionInfo;
import com.intellij.execution.filters.ExceptionLineRefiner;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class DataflowExceptionAnalysisProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testArrayIndex() {
    doTest("java.lang.ArrayIndexOutOfBoundsException: 10",
           "Find why 'idx' could be 10", "class X {static int test(int[] x, int idx) {return x[idx];}}");
  }

  @Test
  public void testClassCast() {
    doTest("java.lang.ClassCastException: class X cannot be cast to class java.lang.Number",
           "Find why 'obj' could be X (non-null)",
           "class X {static void test(Object obj) {System.out.println(((Number) obj).intValue());}}");
  }
  
  @Test
  public void testClassCastUnresolvedTarget() {
    doTest("java.lang.ClassCastException: class X cannot be cast to class foo.Bar",
           "Find why 'obj' could be X (non-null)",
           "class X {static void test(Object obj) {System.out.println(((Bar) obj).intValue());}}");
  }
  
  @Test
  public void testClassCastUnknownClass() {
    doTest("java.lang.ClassCastException: class XYZ cannot be cast to class java.lang.Number",
           "Find why 'obj' could be not instanceof java.lang.Number (non-null)",
           "class X {static void test(Object obj) {System.out.println(((Number) obj).intValue());}}");
  }
  
  @Test
  public void testClassCastGenericArray() {
    //noinspection unchecked
    doTest("java.lang.ClassCastException: class java.lang.String cannot be cast to class [Ljava.lang.Object; (java.lang.String and [Ljava.lang.Object; are in module java.base of loader 'bootstrap')",
           "Find why 'obj' could be java.lang.String (non-null)",
           "class X {static <E> E[] asArray(Object obj) {return (E[])obj;}}");
  }
  
  @Test
  public void testClassCastFromArray() {
    doTest("java.lang.ClassCastException: class [Ljava.lang.String; cannot be cast to class java.lang.String",
           "Find why 'obj' could be java.lang.String[] (non-null)",
           "class X {static String cast(Object obj) {return (String)obj;}}");
  }
  
  @Test
  public void testClassCastFromPrimitiveTwoDimArray() {
    doTest("java.lang.ClassCastException: class [[J cannot be cast to class java.lang.String",
           "Find why 'obj' could be long[][] (non-null)",
           "class X {static String cast(Object obj) {return (String)obj;}}");
  }
  
  @Test
  public void testClassCastFromNested() {
    doTest("java.lang.ClassCastException: class MainTest$X cannot be cast to class java.lang.String",
           "Find why 'obj' could be MainTest.X (non-null)",
           "class MainTest {static String cast(Object obj) {return (String)obj;}static class X {}}");
  }
  
  @Test
  public void testClassCastFromLocal() {
    doTest("java.lang.ClassCastException: class MainTest$1X cannot be cast to class java.lang.String",
           "Find why 'obj' could be X (non-null)",
           "class MainTest {static String cast(Object obj) {return (String)obj;}" +
           "public static void main(String[] args) { class X{}cast(new X()); }}");
  }
  
  @Test
  public void testClassCastFromAnonymous() {
    doTest("java.lang.ClassCastException: class MainTest$1 cannot be cast to class java.lang.String",
           "Find why 'obj' could be anonymous java.lang.Object (non-null)",
           "class MainTest {static String cast(Object obj) {return (String)obj;}" +
           "public static void main(String[] args) { cast(new Object() {});}}");
  }
  
  @Test
  public void testNpe() {
    doTest("java.lang.NullPointerException: Cannot invoke \"Object.hashCode()\" because \"obj\" is null",
           "Find why 'obj' could be null",
           "class X {static void test(Object obj) {System.out.println(obj.hashCode());}}");
  }
  
  @Test
  public void testNpeArray() {
    doTest("java.lang.NullPointerException: Cannot load from object array because \"obj\" is null",
           "Find why 'obj' could be null",
           "class X {static void test(Object[] obj) {System.out.println(obj[0]);}}");
  }
  
  @Test
  public void testNpeSynchronized() {
    doTest("java.lang.NullPointerException: Cannot enter synchronized block because \"obj\" is null",
           "Find why 'obj' could be null",
           "class X {Object lock;void test() {synchronized(obj) {System.out.println(obj);}}}");
  }
  
  @Test
  public void testNpeThrow() {
    doTest("java.lang.NullPointerException: Cannot throw exception because \"obj\" is null",
           "Find why 'obj' could be null",
           "class X {static void test(RuntimeException obj) {throw obj;}}");
  }
  
  @Test
  public void testAssertChar() {
    doTest("java.lang.AssertionError",
           "Find why 'c' could be 97",
           "class X {static void test(char c) {assert c != 'a';}}");
  }
  
  @Test
  public void testAssertDivisibility() {
    doTest("java.lang.AssertionError",
           "Find why 'i % 2' could be != 0",
           "class X {static void test(int i) {assert i % 2 == 0;}}");
  }

  @Test
  public void testAssertAnd() {
    doTest("java.lang.AssertionError",
           "Find why 'idx' could be <= 0 or >= 4",
           "class X {static void test(int idx) {assert idx > 0 && idx < 4;}}");
  }

  @Test
  public void testAssertOr() {
    doTest("java.lang.AssertionError",
           "Find why 'idx' could be in {0..4}",
           "class X {static void test(int idx) {assert idx < 0 || idx > 4;}}");
  }

  @Test
  public void testAssertByte() {
    doTest("java.lang.AssertionError",
           "Find why 'idx' could be <= 0",
           "class X {static void test(byte idx) {assert idx > 0;}}");
  }

  @Test
  public void testAssertShort() {
    doTest("java.lang.AssertionError",
           "Find why 'idx' could be <= 0",
           "class X {static void test(short idx) {assert idx > 0;}}");
  }

  @Test
  public void testAssertLong() {
    doTest("java.lang.AssertionError",
           "Find why 'idx' could be <= 0",
           "class X {static void test(long idx) {assert idx > 0;}}");
  }

  @Test
  public void testAssertBoxed() {
    // boxed types are unsupported
    doTest("java.lang.AssertionError",
           null,
           "class X {static void test(Long idx) {assert idx > 0;}}");
  }

  @Test
  public void testInstanceOf() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'obj' could be null or not instanceof java.lang.String",
           "class X {static void test(Object obj) {if (!(obj instanceof String)) throw new IllegalArgumentException();}}");
  }
  
  @Test
  public void testStringInEquality() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 's' could be != \"hello\" (non-null)",
           "class X {static void test(String s) {if (!s.equals(\"hello\")) throw new IllegalArgumentException();}}");
  }

  @Test
  public void testStringEqEq() {
    doTest("java.lang.IllegalArgumentException",
           null,
           "class X {static void test(String s) {if (s == \"hello\") throw new IllegalArgumentException();}}");
  }

  @Test
  public void testClassEquality() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'cls' could be String",
           "class X {static void test(Class<?> cls) {if (cls.equals(String.class)) throw new IllegalArgumentException();}}");
  }

  @Test
  public void testClassInEquality() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'cls' could be != String (non-null)",
           "class X {static void test(Class<?> cls) {if (!cls.equals(String.class)) throw new IllegalArgumentException();}}");
  }

  @Test
  public void testClassInEqualityInverted() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'cls' could be null or != String",
           "class X {static void test(Class<?> cls) {if (!String.class.equals(cls)) {throw new IllegalArgumentException();}}}");
  }

  @Test
  public void testBooleanTrue() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'b' could be true",
           "class X {static void test(boolean b) {if (b) {throw new IllegalArgumentException();}}}");
  }

  @Test
  public void testBooleanFalse() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'b' could be false",
           "class X {static void test(boolean b) {if (!b) {throw new IllegalArgumentException();}}}");
  }

  @Test
  public void testIsNull() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'obj' could be null",
           "class X {static void test(Object obj) {if (obj == null) {throw new IllegalArgumentException();}}}");
  }

  @Test
  public void testIsNotNull() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'obj' could be non-null",
           "class X {static void test(Object obj) {if (null != obj) {throw new IllegalArgumentException();}}}");
  }

  @Test
  public void testEnumEquality() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'x' could be X.A",
           "enum X {A,B,C;static void test(X x) {if (x == X.A) throw new IllegalArgumentException();}}");
  }
  
  @Test
  public void testInSwitch() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'x' could be 5",
           """
             class X {  static void test(int x) {
                 switch (x) {
                   case 3:
                     System.out.println("oops");
                     break;
                   case 5:
                     throw new IllegalArgumentException();
                 }
               }}""");
  }

  @Test
  public void testInSwitchRule() {
    doTest("java.lang.RuntimeException",
           "Find why 'x' could be in {2, 3, 5}",
           "class X {static void test(int x) { switch (x) { case 2,3,5 -> throw new RuntimeException();default -> {} } }}");
  }

  @Test
  public void testInSwitchDefault() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'x' could be <= 0 or >= 5",
           "class X {static void test(int x) { switch (x) { " +
           "case 1: break; case 2: return; case 3, 4: System.out.println(\"\");break; " +
           "case 5: default: throw new IllegalArgumentException(); } } }");
  }

  @Test
  public void testInSwitchDefaultString() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 's' could be != \"BAR\", \"FOO\" (non-null)",
           "class X {static void test(String s) { switch (s) { " +
           "case \"FOO\": break; case \"BAR\": return;" +
           "default: case \"BAZ\": throw new IllegalArgumentException(); } } }");
  }
  
  @Test
  public void testIfExits() {
    doTest("java.lang.IllegalArgumentException",
           "Find why 'x' could be >= 0",
           """
             class X {  static void test(int x) {
                 if (x < 0) {
                   System.out.println("ok");
                   return;
                 }
                 throw new IllegalArgumentException();
               }}""");
  }
  
  @Test
  public void testNoInfo() {
    doTest("java.lang.IllegalArgumentException",
           null,
           "class X {static void test(X x) {throw new IllegalArgumentException();}}");
  }
  
  @Test
  public void testNegativeArraySizeException() {
    doTest("java.lang.NegativeArraySizeException: -2",
           "Find why 'x' could be -2",
           "class X {static void test(int x) {int[] data = new int[x];}}");
  }
  
  @Test
  public void testDivisionByZero() {
    doTest("java.lang.ArithmeticException: / by zero",
           "Find why 'y' could be 0",
           "class X {static void test(int x, int y) {int[] data = new int[x/y];}}");
  }
  
  @Test
  public void testModByZero() {
    doTest("java.lang.ArithmeticException: / by zero",
           "Find why 'y' could be 0",
           "class X {static void test(int x, long y) {long res = x % y;}}");
  }
  
  @Test
  public void testRequireNonNull() {
    doTestIntermediate("Find why 'str' could be null",
                       "class X {static void test(String str, String msg) {java.util.Objects.<caret>requireNonNull(str, msg);}}");
  }

  @Test
  public void testAssertNull() {
    doTestIntermediate("Find why 'str' could be non-null",
                       "class X {static void test(String str) {<caret>assertNull(str);}" +
                       "static void assertNull(Object obj) {if(obj != null) throw new AssertionError();}}");
  }

  @Test
  public void testAssertTrue() {
    doTestIntermediate("Find why 'x' could be <= 0",
                       "class X {static void test(int x) {<caret>assertTrue(x > 0);}" +
                       "static void assertTrue(boolean flag) {if(!flag) throw new AssertionError();}}");
  }

  @Test
  public void testAssertFalse() {
    doTestIntermediate("Find why 'x' could be >= 1",
                       "class X {static void test(int x) {<caret>assertFalse(x > 0);}" +
                       "static void assertFalse(boolean flag) {if(flag) throw new AssertionError();}}");
  }
  
  @Test
  public void testAssertTrueUnboxing() {
    doTestIntermediate("Find why 'x' could be false",
                       "class X {static void test(Boolean x) {<caret>assertTrue(x);}" +
                       "static void assertTrue(boolean flag) {if(!flag) throw new AssertionError();}}");
  }

  @Test
  public void testOptionalGet() {
    // Not supported
    doTestIntermediate(null,
                       "class X {static void test(java.util.Optional x) {x.<caret>get();}}");
  }

  @Test
  public void testNpeJetBrains() {
    doTest("""
             java.lang.IllegalArgumentException: Argument for @NotNull parameter 'y' of foo/bar/Test.callee must not be null
             \tat foo.bar.Test.$$$reportNull$$$0(Test.java)
             \tat foo.bar.Test.callee(Test.java)""",
           "Find why 'b' could be null",
           """
             package foo.bar;
             class Test {
               void caller(String a, String b, String c) {
                 callee(a, b, c);
               }

               void callee(String x, String y, String z) {}
             }""");
  }
  
  @Test
  public void testNpeJetBrainsOverride() {
    doTest("""
             Exception in thread "main" java.lang.IllegalArgumentException: Argument for @NotNull parameter 's' of MainTest$XImpl.foo must not be null
             \tat MainTest$XImpl.$$$reportNull$$$0(MainTest.java)
             \tat MainTest$XImpl.foo(MainTest.java)""",
           "Find why 's1' could be null",
           """
             import org.jetbrains.annotations.NotNull;

             public class MainTest {
                 static void test(X x, String s, String s1) { x.foo(s, s1); }
                 interface X { void foo(String s, String t);}
                 static class XImpl implements X { @Override public void foo(String t, @NotNull String s) {}}    public static void main(String[] args) { test(new XImpl(), "", null); }}""");
  }
  
  @Test
  public void testArrayCopySource() {
    doTest("java.lang.ArrayIndexOutOfBoundsException: arraycopy: source index -1 out of bounds for int[10]\n" +
           "\tat java.base/java.lang.System.arraycopy(Native Method)",
           "Find why 'src' could be -1",
           "class Test {static void test(int[] data, int src, int dst, int len) { System.arraycopy(data, src, data, dst, len); }}");
  }
  
  @Test
  public void testArrayCopyDest() {
    doTest("java.lang.ArrayIndexOutOfBoundsException: arraycopy: destination index -1 out of bounds for int[10]\n" +
           "\tat java.base/java.lang.System.arraycopy(Native Method)",
           "Find why 'dst' could be -1",
           "class Test {static void test(int[] data, int src, int dst, int len) { System.arraycopy(data, src, data, dst, len); }}");
  }
  
  @Test
  public void testArrayCopyLength() {
    doTest("java.lang.ArrayIndexOutOfBoundsException: arraycopy: length -1 is negative\n" +
           "\tat java.base/java.lang.System.arraycopy(Native Method)",
           "Find why 'len' could be -1",
           "class Test {static void test(int[] data, int src, int dst, int len) { System.arraycopy(data, src, data, dst, len); }}");
  }

  private void doTest(@NotNull String exceptionLine,
                      @Nullable("If no action is expected") String expectedActionTitle,
                      @NotNull @Language("JAVA") String source) {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureFromFileText("Test.java", source);
    ReadAction.runBlocking(() -> {
      ExceptionAnalysisProvider analysisProvider = myFixture.getProject().getService(ExceptionAnalysisProvider.class);
      String[] lines = exceptionLine.split("\n");
      ExceptionInfo info = ExceptionInfo.parseMessage(lines[0], 0);
      assertNotNull(info);
      for (int i = 1; i < lines.length; i++) {
        info = info.consumeStackLine(lines[i]);
        assertNotNull(info, lines[i]);
      }
      ExceptionLineRefiner refiner = info.getPositionRefiner();
      PsiElement leaf = myFixture.getFile().findElementAt(0);
      PsiElement anchor = null;
      while (leaf != null) {
        ExceptionLineRefiner.RefinerMatchResult candidate = refiner.matchElement(leaf);
        if (candidate != null) {
          if (anchor == null || anchor == candidate.reason()) {
            anchor = candidate.reason();
          }
          else {
            fail("Two candidates found: " + anchor.getText());
          }
        }
        leaf = PsiTreeUtil.nextLeaf(leaf);
      }
      assertNotNull(anchor, "No anchors found");
      AnAction action = analysisProvider.getAnalysisAction(anchor, info, Collections::emptyList);
      checkAction(action, expectedActionTitle);
    });
  }

  private void doTestIntermediate(@Nullable("If no action is expected") String expectedActionTitle,
                                  @NotNull String source) {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureFromFileText("Test.java", source);
    ReadAction.runBlocking(() -> {
      ExceptionAnalysisProvider analysisProvider = myFixture.getProject().getService(ExceptionAnalysisProvider.class);
      int offset = myFixture.getEditor().getCaretModel().getOffset();
      assertTrue(offset > 0, "Offset is not set");
      PsiElement leaf = myFixture.getFile().findElementAt(offset);
      AnAction action = analysisProvider.getIntermediateRowAnalysisAction(leaf, Collections::emptyList);
      checkAction(action, expectedActionTitle);
    });
  }

  private static void checkAction(AnAction action, @Nullable("If no action is expected") String expectedActionTitle) {
    if (expectedActionTitle == null) {
      assertNull(action);
    }
    else {
      assertNotNull(action);
      String text = action.getTemplatePresentation().getDescription();
      assertEquals(expectedActionTitle, text);
    }
  }
}
