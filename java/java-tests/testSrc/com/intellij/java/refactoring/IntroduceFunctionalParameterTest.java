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
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;

@TestDataPath("$CONTENT_ROOT/testData")
public class IntroduceFunctionalParameterTest extends LightRefactoringTestCase  {
  public void testSampleRunnable() {
    doTest();
  }

  public void testIntConsumer() {
    doTest();
  }

  public void testFunction() {
    doTest();
  }

  public void testIntConsumerFromIfStatement() {
    doTest();
  }

  public void testIntPredicateConditionalExit() {
    doTest();
  }

  public void testExceptionPreventFromCompatibility() {
    doTest();
  }
  
  public void testThrownExceptionsAgree() {
    doTest();
  }

  public void testEnsureNotFolded() {
    doTest();
  }

  public void testInsideForLoop() {
    doTest();
  }

  public void testInsideAnonymous() {
    doTest();
  }

  public void testPartialString() {
    doTest();
  }

  public void testUsedParametersOutsideSelectedFragment() {
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
        PsiElement[] elements = ExtractMethodHandler.getElements(getProject(), getEditor(), getFile());
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
