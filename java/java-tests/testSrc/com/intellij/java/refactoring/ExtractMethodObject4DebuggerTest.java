// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.refactoring;

import com.intellij.idea.TestFor;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.intellij.refactoring.extractMethodObject.LightMethodObjectExtractedData;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.RegistryKey;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
@RegistryKey(key = "debugger.compiling.evaluator.magic.accessor", value = "true")
@RegistryKey(key = "debugger.compiling.evaluator.reflection.access.with.java8", value = "false")
public class ExtractMethodObject4DebuggerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  private void doTest(String evaluatedText, String expectedCallSite, String expectedClass) throws Exception {
    doTest(evaluatedText, expectedCallSite, expectedClass, true);
  }

  private void doTest(String evaluatedText,
                      String expectedCallSite,
                      String expectedClass,
                      boolean codeBlock) throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false);
    myFixture.configureByFile("/refactoring/extractMethodObject4Debugger/" + testName + ".java");
    myFixture.runInCommand(() -> {
      final int offset = myFixture.getEditor().getCaretModel().getOffset();
      final PsiElement context = myFixture.getFile().findElementAt(offset);
      final JavaCodeFragmentFactory fragmentFactory = JavaCodeFragmentFactory.getInstance(myFixture.getProject());
      final JavaCodeFragment fragment = codeBlock ? fragmentFactory.createCodeBlockCodeFragment(evaluatedText, context, false) : fragmentFactory.createExpressionCodeFragment(evaluatedText, context, null, false);
      final LightMethodObjectExtractedData extractedData =
        ExtractLightMethodObjectHandler.extractLightMethodObject(myFixture.getProject(), context, fragment, "test", JavaSdkVersion.JDK_1_8);
      assertNotNull(extractedData);
      assertEquals(expectedCallSite, extractedData.getGeneratedCallText());
      final PsiClass innerClass = extractedData.getGeneratedInnerClass();
      assertEquals(expectedClass, innerClass.getText());
    });
  }

  @Test
  public void testSimpleGeneration() throws Exception {
    doTest("int i = 0; int j = 0;", "Test test = new Test().invoke();int i = test.getI();int j = test.getJ();",

           """
             static class Test {
                     private int i;
                     private int j;

                     public int getI() {
                         return i;
                     }

                     public int getJ() {
                         return j;
                     }

                     public Test invoke() {
                         i = 0;
                         j = 0;
                         return this;
                     }
                 }""");
  }

  @Test
  public void testInvokeReturnType() throws Exception {
    doTest("x = 6; y = 6;", "Test test = new Test().invoke();x = test.getX();y = test.getY();int result = test.getResult();",

           """
             static class Test {
                     private int x;
                     private int y;
                     private int result;
       
                     public int getX() {
                         return x;
                     }
       
                     public int getY() {
                         return y;
                     }
       
                     public int getResult() {
                         return result;
                     }
       
                     public Test invoke() {
                         x = 6;
                         result = y = 6;
                         return this;
                     }
                 }""");
  }

  @Test
  public void testAnonymousClassParams() throws Exception {
    doTest("new I() {public void foo(int i) {i++;}};", "I result = Test.invoke();",

           """
             static class Test {
                     static I invoke() {
                         return new I() {
                             public void foo(int i) {
                                 i++;
                             }
                         };
                     }
                 }""");
  }

  @Test
  public void testInnerClass() throws Exception {
    doTest("   new I(2).foo()", "new Test().invoke();",

           """
             class Test {
                     public void invoke() {
                         new Sample.I(2).foo();
                     }
                 }""");
  }

  @Test
  public void testResultExpr() throws Exception {
    doTest("   foo()", "int result = new Test().invoke();",

           """
             class Test {
                     public int invoke() {
                         return foo();
                     }
                 }""");
  }

  @Test
  public void testResultStatements() throws Exception {
    doTest("int i = 0;\nfoo()", "Test test = new Test().invoke();int i = test.getI();int result = test.getResult();",

           """
             class Test {
                     private int i;
                     private int result;

                     public int getI() {
                         return i;
                     }

                     public int getResult() {
                         return result;
                     }

                     public Test invoke() {
                         i = 0;
                         result = foo();
                         return this;
                     }
                 }""");
  }


  @Test
  public void testOffsetsAtCallSite() throws Exception {
    doTest("map.entrySet().stream().filter((a) -> (a.getKey()>0));",
           "Stream<Map.Entry<Integer,Integer>> result = new Test(map).invoke();",
           """
             static class Test {
                     private Map<Integer, Integer> map;

                     public Test(Map<Integer, Integer> map) {
                         this.map = map;
                     }

                     public Stream<Map.Entry<Integer, Integer>> invoke() {
                         return map.entrySet().stream().filter((a) -> (a.getKey() > 0));
                     }
                 }""");
  }

  @Test
  public void testHangingFunctionalExpressions() throws Exception {
    doTest("() -> {}", "Test.invoke();", """
      static class Test {
              static void invoke() {
                  () -> {
                  };
              }
          }""");
  }

  @Test
  public void testArrayInitializer() throws Exception {
    doTest("{new Runnable() {public void run(){} } }",
           "Runnable[] result = Test.invoke();",
           """
             static class Test {
                     static Runnable[] invoke() {
                         return new Runnable[]{new Runnable() {
                             public void run() {
                             }
                         }};
                     }
                 }""", false);
  }

  @Test
  public void testNewArrayInitializer() throws Exception {
    doTest("new Runnable[] {new Runnable() {public void run(){} } }",
           "Runnable[] result = Test.invoke();",
           """
             static class Test {
                     static Runnable[] invoke() {
                         return new Runnable[]{new Runnable() {
                             public void run() {
                             }
                         }};
                     }
                 }""", false);
  }

  @Test
  public void testThisAndSuperReferences() throws Exception {
    doTest("""
             list.forEach(i -> {
                   new Runnable() {
                     int xxx = 0;
                     @Override
                     public void run() {
                       this.xxx = 5; // this stays the same
                     }
                   }.run();
                   this.a++;  // have to be qualified
                   super.foo();  // have to be qualified
                   a++;
                   foo();
                 });""",
           "new Test(list).invoke();",
           """
             class Test {
                     private List<Integer> list;

                     public Test(List<Integer> list) {
                         this.list = list;
                     }

                     public void invoke() {
                         list.forEach(i -> {
                             new Runnable() {
                                 int xxx = 0;

                                 @Override
                                 public void run() {
                                     this.xxx = 5; // this stays the same
                                 }
                             }.run();
                             Sample.this.a++;  // have to be qualified
                             Sample.super.foo();  // have to be qualified
                             a++;
                             foo();
                         });
                     }
                 }""", false);
  }

  @Test
  public void testOnClosingBrace() throws Exception {
    doTest("   foo()", "int result = new Test().invoke();",

           """
             class Test {
                     public int invoke() {
                         return foo();
                     }
                 }""");
  }

  @Test
  public void testOnClosingBraceLocalClass() throws Exception {
    doTest("   foo()", "int result = new Test().invoke();",

           """
             class Test {
                     public int invoke() {
                         return foo();
                     }
                 }""");
  }

  @Test
  public void testOnFieldInitialization() throws Exception {
    doTest("   foo()", "int result = new Test().invoke();",

           """
             class Test {
                     public int invoke() {
                         return foo();
                     }
                 }""");
  }

  @Test
  public void testOnEmptyMethod() throws Exception {
    doTest("   foo()", "int result = Test.invoke();",

           """
             static class Test {
                     static int invoke() {
                         return foo();
                     }
                 }""");
  }

  @Test
  public void testOnSuperConstructorCall() throws Exception {
    doTest("   foo()", "int result = new Test().invoke();",

           """
             class Test {
                     public int invoke() {
                         return foo();
                     }
                 }""");
  }

  @Test
  public void testOnPrivateField() throws Exception {
    doTest("   foo()", "int result = new Test().invoke();",

           """
             class Test {
                     public int invoke() {
                         return foo();
                     }
                 }""");
  }

  @TestFor(issues = "IDEA-370817")
  @Test
  public void testOnWithComment() throws Exception {
    doTest("""
             //
             System.out.println("abc");
             """, "Test.invoke();",

           """
             static class Test {
                     static void invoke() {
                         System.out.println("abc");
                     }
                 }""");
  }

  @TestFor(issues = "IDEA-396115")
  @Test
  public void testInSwitchRuleBody() throws Exception {
    doTest("i", "Integer result = new Test(i).invoke();",
           """
             static class Test {
                     private Integer i;

                     public Test(Integer i) {
                         this.i = i;
                     }

                     public Integer invoke() {
                         return i;
                     }
                 }""", false);
  }

  @TestFor(issues = "IDEA-396115")
  @Test
  public void testInSwitchRuleThrowBody() throws Exception {
    doTest("i", "Integer result = new Test(i).invoke();",
           """
             static class Test {
                     private Integer i;
             
                     public Test(Integer i) {
                         this.i = i;
                     }
             
                     public Integer invoke() {
                         return i;
                     }
                 }""", false);
  }

  @TestFor(issues = "IDEA-396115")
  @Test
  public void testInSwitchExpressionRuleBody() throws Exception {
    doTest("i", "Integer result = new Test(i).invoke();",
           """
             static class Test {
                     private Integer i;

                     public Test(Integer i) {
                         this.i = i;
                     }

                     public Integer invoke() {
                         return i;
                     }
                 }""", false);
  }
}
