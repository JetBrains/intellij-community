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
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.intention.impl.MakeSealedAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class MakeSealedActionFailingTest extends LightJavaCodeInsightTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_15;
  }

  public void testFunctionalInterface() {
    checkErrorMessage(JavaBundle.message("intention.error.make.sealed.class.is.used.in.functional.expression"));
  }

  public void testAnonymousClass() {
    checkErrorMessage(JavaBundle.message("intention.error.make.sealed.class.has.anonymous.inheritors"));
  }

  private void checkErrorMessage(@NotNull String message) {
    configureByFile("/codeInsight/daemonCodeAnalyzer/quickFix/makeClassSealed/failing/" + getTestName(false) + ".java");
    MakeSealedAction action = new MakeSealedAction();
    assertTrue(action.isAvailable(getProject(), getEditor(), getFile()));
    try {
      ApplicationManager.getApplication().runWriteAction(() -> action.invoke(getProject(), getEditor(), getFile()));
    } catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(message, e.getMessage());
      return;
    }
    fail("Test must fail with error message");
  }
}
