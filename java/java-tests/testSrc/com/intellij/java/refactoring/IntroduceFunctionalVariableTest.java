// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public void testExpressionNoVarsSelected() {
    doTest();
  }

  public void testExpressionInLoopNoVars() {
    doTest();
  }

  public void testStatementInLoop() {
    doTest();
  }
  
  public void testStatements() {
    doTest();
  }

  public void testPassFieldsAsParameters() {
    doTest();
  }

  public void testSkipUsedLocals() {
    doTest(0);
  }

  public void testRunnableFromComment() {
    doTest();
  }

  public void testChangeContextBeforePuttingIntoAnonymous() {
    doTest();
  }

  public void testIgnoreMethodObjectSuggestion() {
    try {
      doTest();
      fail("Unable to perform is expected");
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot perform refactoring.\n" +
                   "There are multiple output values for the selected code fragment:\n" +
                   "    x : int,\n" +
                   "    y : int.", e.getMessage());
    }
  }

  public void testNoSuggestionForInaccessibleInterface() {
    try {
      doTest();
      fail("Should be shown a error hint");
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("No applicable functional interfaces found", e.getMessage());
    }
  }

  public void testPostfixExpressionUnusedAfterAssignment() {
    doTest();
  }

  public void testFieldFromSuperClassPreserveContext() {
    doTest();
  }

  private void doTest(int... disableParams) {
    boolean enabled = true;
    try {
      configureByFile("/refactoring/introduceFunctionalVariable/before" + getTestName(false) + ".java");
      enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);
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
