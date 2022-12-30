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

package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.intellij.refactoring.extractMethodObject.LightMethodObjectExtractedData;
import org.jetbrains.annotations.NotNull;

public class ExtractMethodObject4DebuggerTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest(String evaluatedText, String expectedCallSite, String expectedClass) throws Exception {
    doTest(evaluatedText, expectedCallSite, expectedClass, true);
  }

  private void doTest(String evaluatedText,
                      String expectedCallSite,
                      String expectedClass,
                      boolean codeBlock) throws Exception {
    final String testName = getTestName(false);
    configureByFile("/refactoring/extractMethodObject4Debugger/" + testName + ".java");
    final int offset = getEditor().getCaretModel().getOffset();
    final PsiElement context = getFile().findElementAt(offset);
    final JavaCodeFragmentFactory fragmentFactory = JavaCodeFragmentFactory.getInstance(getProject());
    final JavaCodeFragment fragment = codeBlock ? fragmentFactory.createCodeBlockCodeFragment(evaluatedText, context, false) : fragmentFactory.createExpressionCodeFragment(evaluatedText, context, null, false);
    final LightMethodObjectExtractedData extractedData =
      ExtractLightMethodObjectHandler.extractLightMethodObject(getProject(), context, fragment, "test", JavaSdkVersion.JDK_1_8);
    assertNotNull(extractedData);
    assertEquals(expectedCallSite, extractedData.getGeneratedCallText());
    final PsiClass innerClass = extractedData.getGeneratedInnerClass();
    assertEquals(expectedClass, innerClass.getText());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("debugger.compiling.evaluator.magic.accessor").setValue(true);
    Registry.get("debugger.compiling.evaluator.reflection.access.with.java8").setValue(false);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Registry.get("debugger.compiling.evaluator.magic.accessor").resetToDefault();
      Registry.get("debugger.compiling.evaluator.reflection.access.with.java8").resetToDefault();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

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

  public void testInvokeReturnType() throws Exception {
    doTest("x = 6; y = 6;", "Test test = new Test().invoke();x = test.getX();y = test.getY();",

           """
             static class Test {
                     private int x;
                     private int y;

                     public int getX() {
                         return x;
                     }

                     public int getY() {
                         return y;
                     }

                     public Test invoke() {
                         x = 6;
                         y = 6;
                         return this;
                     }
                 }""");
  }

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

  public void testInnerClass() throws Exception {
    doTest("   new I(2).foo()", "new Test().invoke();",

           """
             class Test {
                     public void invoke() {
                         new Sample.I(2).foo();
                     }
                 }""");
  }

  public void testResultExpr() throws Exception {
    doTest("   foo()", "int result = new Test().invoke();",

           """
             class Test {
                     public int invoke() {
                         return foo();
                     }
                 }""");
  }

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

  public void testHangingFunctionalExpressions() throws Exception {
    doTest("() -> {}", "Test.invoke();", """
      static class Test {
              static void invoke() {
                  () -> {
                  };
              }
          }""");
  }

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

  public void testOnClosingBrace() throws Exception {
    doTest("   foo()", "int result = new Test().invoke();",

           """
             class Test {
                     public int invoke() {
                         return foo();
                     }
                 }""");
  }

  public void testOnClosingBraceLocalClass() throws Exception {
    doTest("   foo()", "int result = new Test().invoke();",

           """
             class Test {
                     public int invoke() {
                         return foo();
                     }
                 }""");
  }

  public void testOnFieldInitialization() throws Exception {
    doTest("   foo()", "int result = new Test().invoke();",

           """
             class Test {
                     public int invoke() {
                         return foo();
                     }
                 }""");
  }

  public void testOnEmptyMethod() throws Exception {
    doTest("   foo()", "int result = Test.invoke();",

           """
             static class Test {
                     static int invoke() {
                         return foo();
                     }
                 }""");
  }

  public void testOnSuperConstructorCall() throws Exception {
    doTest("   foo()", "int result = new Test().invoke();",

           """
             class Test {
                     public int invoke() {
                         return foo();
                     }
                 }""");
  }

  public void testOnPrivateField() throws Exception {
    doTest("   foo()", "int result = new Test().invoke();",

           """
             class Test {
                     public int invoke() {
                         return foo();
                     }
                 }""");
  }
}
