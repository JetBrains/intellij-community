// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.introduceVariable.IntroduceFunctionalVariableHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestDataPath("$CONTENT_ROOT/testData")
@TestApplication
public class IntroduceFunctionalVariableTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  

  @Test
  public void testExpressionNoVarsSelected() {
    doTest();
  }

  @Test
  public void testExpressionInLoopNoVars() {
    doTest();
  }

  @Test
  public void testStatementInLoop() {
    doTest();
  }
  
  @Test
  public void testStatements() {
    doTest();
  }

  @Test
  public void testPassFieldsAsParameters() {
    doTest();
  }

  @Test
  public void testSkipUsedLocals() {
    doTest(0);
  }

  @Test
  public void testRunnableFromComment() {
    doTest();
  }

  @Test
  public void testChangeContextBeforePuttingIntoAnonymous() {
    doTest();
  }

  @Test
  public void testIgnoreMethodObjectSuggestion() {
    try {
      doTest();
      fail("Unable to perform is expected");
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("""
                     Cannot perform refactoring.
                     There are multiple output values for the selected code fragment:
                         x : int,
                         y : int.""", e.getMessage());
    }
  }

  @Test
  public void testNoSuggestionForInaccessibleInterface() {
    try {
      doTest();
      fail("Should be shown a error hint");
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("No applicable functional interfaces found", e.getMessage());
    }
  }

  @Test
  public void testPostfixExpressionUnusedAfterAssignment() {
    doTest();
  }

  @Test
  public void testFieldFromSuperClassPreserveContext() {
    doTest();
  }

  private void doTest(int... disableParams) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      boolean enabled = true;
      try {
        myFixture.configureByFile("/refactoring/introduceFunctionalVariable/before" + myFixture.getTestName(false) + ".java");
        enabled = myFixture.getEditor().getSettings().isVariableInplaceRenameEnabled();
        myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(true);
        new IntroduceFunctionalVariableHandler() {
          @Override
          protected void setupProcessorWithoutDialog(ExtractMethodProcessor processor, InputVariables inputVariables) {
            inputVariables.setPassFields(true);
            super.setupProcessorWithoutDialog(processor, inputVariables);
            for (int i : disableParams) {
              processor.doNotPassParameter(i);
            }
          }
        }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), new MapDataContext());
        myFixture.checkResultByFile("/refactoring/introduceFunctionalVariable/after" + myFixture.getTestName(false) + ".java");
      } finally {
        myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
      }
    });
  }
}
