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
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;

@TestDataPath("$CONTENT_ROOT/testData")
public class IntroduceFunctionalParameterTest extends LightRefactoringTestCase  {
  public void testSampleRunnable() throws Exception {
    doTest();
  }

  public void testIntConsumer() throws Exception {
    doTest();
  }

  public void testFunction() throws Exception {
    doTest();
  }

  public void testIntConsumerFromIfStatement() throws Exception {
    doTest();
  }

  public void testIntPredicateConditionalExit() throws Exception {
    doTest();
  }

  public void testExceptionPreventFromCompatibility() throws Exception {
    doTest();
  }
  
  public void testThrownExceptionsAgree() throws Exception {
    doTest();
  }

  public void testEnsureNotFolded() throws Exception {
    doTest();
  }

  public void testInsideForLoop() throws Exception {
    doTest();
  }

  public void testInsideAnonymous() throws Exception {
    doTest();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }

  private void doTest() {
    doTest(null);
  }

  private void doTest(String conflict) {
    boolean enabled = true;
    try {
      configureByFile("/refactoring/introduceFunctionalParameter/before" + getTestName(false) + ".java");
      enabled = myEditor.getSettings().isVariableInplaceRenameEnabled();
      myEditor.getSettings().setVariableInplaceRenameEnabled(false);
      final SelectionModel selectionModel = getEditor().getSelectionModel();
      if (selectionModel.hasSelection()) {
        final int selectionStart = selectionModel.getSelectionStart();
        final int selectionEnd = selectionModel.getSelectionEnd();
        PsiElement[] elements = CodeInsightUtil.findStatementsInRange(getFile(), selectionStart, selectionEnd);
        if (elements.length == 0) {
          final PsiExpression expression = CodeInsightUtil.findExpressionInRange(getFile(), selectionStart, selectionEnd);
          if (expression != null) {
            elements = new PsiElement[] {expression};
          }
        }
        new IntroduceParameterHandler().introduceStrategy(getProject(), getEditor(), getFile(), elements);
      }
      checkResultByFile("/refactoring/introduceFunctionalParameter/after" + getTestName(false) + ".java");
      if (conflict != null) {
        fail("Conflict expected");
      }
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (conflict == null) {
        throw e;
      }
      assertEquals(conflict, e.getMessage());
    } finally {
      myEditor.getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  
}
