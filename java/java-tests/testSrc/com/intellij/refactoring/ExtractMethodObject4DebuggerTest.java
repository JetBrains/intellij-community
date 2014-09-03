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
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

public class ExtractMethodObject4DebuggerTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest(String expectedCallSite, String expectedClass) throws Exception {
    final String testName = getTestName(false);
    configureByFile("/refactoring/extractMethodObject4Debugger/" + testName + ".java");
    int startOffset = getEditor().getSelectionModel().getSelectionStart();
    int endOffset = getEditor().getSelectionModel().getSelectionEnd();
    PsiElement[] elements = CodeInsightUtil.findStatementsInRange(getFile(), startOffset, endOffset);
    assertTrue(elements.length > 0);

    final ExtractLightMethodObjectHandler.ExtractedData extractedData =
      ExtractLightMethodObjectHandler.extractLightMethodObject(getProject(), getEditor(), getFile(), elements, "test");
    assertNotNull(extractedData);
    assertEquals(expectedCallSite, extractedData.getGeneratedCallText());
    final PsiClass innerClass = extractedData.getGeneratedInnerClass();
    assertEquals(expectedClass, innerClass.getText());
  }

  public void testSimpleGeneration() throws Exception {
    doTest("  Test test = new Test().invoke();\n" +
           "      int i = test.getI();\n" +
           "      int j = test.getJ();",

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

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }
}
