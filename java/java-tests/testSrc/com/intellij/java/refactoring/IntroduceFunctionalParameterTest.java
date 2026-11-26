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

import com.intellij.openapi.editor.SelectionModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.testFramework.PlatformTestUtil;
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
public class IntroduceFunctionalParameterTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testSampleRunnable() {
    doTest();
  }

  @Test
  public void testIntConsumer() {
    doTest();
  }

  @Test
  public void testFunction() {
    doTest();
  }

  @Test
  public void testIntConsumerFromIfStatement() {
    doTest();
  }

  @Test
  public void testIntPredicateConditionalExit() {
    doTest();
  }

  @Test
  public void testExceptionPreventFromCompatibility() {
    doTest();
  }
  
  @Test
  public void testThrownExceptionsAgree() {
    doTest();
  }

  @Test
  public void testEnsureNotFolded() {
    doTest();
  }

  @Test
  public void testInsideForLoop() {
    doTest();
  }

  @Test
  public void testInsideAnonymous() {
    doTest();
  }

  @Test
  public void testPartialString() {
    doTest();
  }

  @Test
  public void testUsedParametersOutsideSelectedFragment() {
    doTest();
  }

  private void doTest() {
    doTest(null);
  }

  private void doTest(String conflict) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      boolean enabled = true;
      try {
        myFixture.configureByFile("/refactoring/introduceFunctionalParameter/before" + myFixture.getTestName(false) + ".java");
        enabled = myFixture.getEditor().getSettings().isVariableInplaceRenameEnabled();
        myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(false);
        final SelectionModel selectionModel = myFixture.getEditor().getSelectionModel();
        if (selectionModel.hasSelection()) {
          PsiElement[] elements = ExtractMethodHandler.getElements(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
          new IntroduceParameterHandler().introduceStrategy(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), elements);
          PlatformTestUtil.dispatchAllEventsInIdeEventQueue(); // let all the invokeLater to pass through
        }
        myFixture.checkResultByFile("/refactoring/introduceFunctionalParameter/after" + myFixture.getTestName(false) + ".java");
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
        myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
      }
    });
  }

  
}
