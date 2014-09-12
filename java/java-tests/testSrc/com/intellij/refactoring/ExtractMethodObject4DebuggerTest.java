/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.idea.Bombed;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.*;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Calendar;

public class ExtractMethodObject4DebuggerTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest(String evaluatedText, String expectedCallSite, String expectedClass) throws Exception {
    final String testName = getTestName(false);
    configureByFile("/refactoring/extractMethodObject4Debugger/" + testName + ".java");
    final int offset = getEditor().getCaretModel().getOffset();
    final PsiElement context = getFile().findElementAt(offset);
    final JavaCodeFragment fragment = JavaCodeFragmentFactory.getInstance(getProject()).createCodeBlockCodeFragment(evaluatedText, context, false);
    final ExtractLightMethodObjectHandler.ExtractedData extractedData =
      ExtractLightMethodObjectHandler.extractLightMethodObject(getProject(), getFile(), fragment, "test");
    assertNotNull(extractedData);
    assertEquals(expectedCallSite, extractedData.getGeneratedCallText());
    final PsiClass innerClass = extractedData.getGeneratedInnerClass();
    assertEquals(expectedClass, innerClass.getText());
  }

  public void testSimpleGeneration() throws Exception {
    doTest("int i = 0; int j = 0;", "Test test = new Test().invoke();int i = test.getI();int j = test.getJ();",

           "public class Test {\n" +
           "        private int i;\n" +
           "        private int j;\n" +
           "\n" +
           "        public int getI() {\n" +
           "            return i;\n" +
           "        }\n" +
           "\n" +
           "        public int getJ() {\n" +
           "            return j;\n" +
           "        }\n" +
           "\n" +
           "        public Test invoke() {\n" +
           "            i = 0;\n" +
           "            j = 0;\n" +
           "            return this;\n" +
           "        }\n" +
           "    }");
  }

  public void testInvokeReturnType() throws Exception {
    doTest("x = 6; y = 6;", "Test test = new Test().invoke();x = test.getX();y = test.getY();",

           "public static class Test {\n" +
           "        private int x;\n" +
           "        private int y;\n" +
           "\n" +
           "        public int getX() {\n" +
           "            return x;\n" +
           "        }\n" +
           "\n" +
           "        public int getY() {\n" +
           "            return y;\n" +
           "        }\n" +
           "\n" +
           "        public Test invoke() {\n" +
           "            x = 6;\n" +
           "            y = 6;\n" +
           "            return this;\n" +
           "        }\n" +
           "    }");
  }

  public void testAnonymousClassParams() throws Exception {
    doTest("new I() {public void foo(int i) {i++;}};", "I result = new Test().invoke();",

           "public class Test {\n" +
           "        public I invoke() {\n" +
           "            return new I() {\n" +
           "                public void foo(int i) {\n" +
           "                    i++;\n" +
           "                }\n" +
           "            };\n" +
           "        }\n" +
           "    }");
  }

  public void testInnerClass() throws Exception {
    doTest("   new I(2).foo()", "new Test().invoke();",

           "public class Test {\n" +
           "        public void invoke() {\n" +
           "            new Sample.I(2).foo();\n" +
           "        }\n" +
           "    }");
  }

  public void testResultExpr() throws Exception {
    doTest("   foo()", "int result = new Test().invoke();",

           "public class Test {\n" +
           "        public int invoke() {\n" +
           "            return foo();\n" +
           "        }\n" +
           "    }");
  }

  public void testResultStatements() throws Exception {
    doTest("int i = 0;\nfoo()", "Test test = new Test().invoke();int i = test.getI();int result = test.getResult();",

           "public class Test {\n" +
           "        private int i;\n" +
           "        private int result;\n" +
           "\n" +
           "        public int getI() {\n" +
           "            return i;\n" +
           "        }\n" +
           "\n" +
           "        public int getResult() {\n" +
           "            return result;\n" +
           "        }\n" +
           "\n" +
           "        public Test invoke() {\n" +
           "            i = 0;\n" +
           "            result = foo();\n" +
           "            return this;\n" +
           "        }\n" +
           "    }");
  }


  public void testOffsetsAtCallSite() throws Exception {
    doTest("map.entrySet().stream().filter((a) -> (a.getKey()>0));",
           "java.util.stream.Stream<Map.Entry<Integer,Integer>> result = new Test(map).invoke();",
           "public class Test {\n" +
           "        private Map<Integer, Integer> map;\n" +
           "\n" +
           "        public Test(Map<Integer, Integer> map) {\n" +
           "            this.map = map;\n" +
           "        }\n" +
           "\n" +
           "        public Stream<Map.Entry<Integer, Integer>> invoke() {\n" +
           "            return map.entrySet().stream().filter((a) -> (a.getKey() > 0));\n" +
           "        }\n" +
           "    }");
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }
}
