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
import com.intellij.pom.java.LanguageLevel;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.introduceVariable.IntroduceFunctionalVariableHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;

@TestDataPath("$CONTENT_ROOT/testData")
public class IntroduceFunctionalVariableTest extends LightRefactoringTestCase  {
  
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }

  public void testExpressionNoVarsSelected() throws Exception {
    doTest();
  }

  public void testExpressionInLoopNoVars() throws Exception {
    doTest();
  }

  public void testStatementInLoop() throws Exception {
    doTest();
  }
  
  public void testStatements() throws Exception {
    doTest();
  }

  public void testPassFieldsAsParameters() throws Exception {
    doTest();
  }

  public void testSkipUsedLocals() throws Exception {
    doTest(0);
  }

  public void testRunnableFromComment() throws Exception {
    doTest();
  }

  public void testChangeContextBeforePuttingIntoAnonymous() throws Exception {
    doTest();
  }

  public void testIgnoreMethodObjectSuggestion() throws Exception {
    try {
      doTest();
      fail("Unable to perform is expected");
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot perform refactoring.\n" +
                   "Extract Functional Variable is not supported in current context", e.getMessage());
    }
  }

  public void testNoSuggestionForInaccessibleInterface() throws Exception {
    try {
      doTest();
      fail("Should be shown a error hint");
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("No applicable functional interfaces found", e.getMessage());
    }
  }

  public void testPostfixExpressionUnusedAfterAssignment() throws Exception {
    doTest();
  }

  public void testFieldFromSuperClassPreserveContext() throws Exception {
    doTest();
  }

  private void doTest(int... disableParams) {
    boolean enabled = true;
    try {
      configureByFile("/refactoring/introduceFunctionalVariable/before" + getTestName(false) + ".java");
      enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
      getEditor().getSettings().setVariableInplaceRenameEnabled(false);
      new IntroduceFunctionalVariableHandler() {
        @Override
        protected void setupProcessorWithoutDialog(ExtractMethodProcessor processor, InputVariables inputVariables) {
          inputVariables.setPassFields(true);
          super.setupProcessorWithoutDialog(processor, inputVariables);
          for (int i : disableParams) {
            processor.doNotPassParameter(i);
          }
        }
      }.invoke(getProject(), getEditor(), getFile(), new MapDataContext());
      checkResultByFile("/refactoring/introduceFunctionalVariable/after" + getTestName(false) + ".java");
    } finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }
}
