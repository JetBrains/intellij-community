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

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class InlineSameParameterValueTest extends LightQuickFixParameterizedTestCase {
  private static final String CONFLICT_SUFFIX = "Conflict.java";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  public void test() {
    doAllTests();
  }

  @Override
  protected void doAction(@NotNull final ActionHint actionHint, final String testFullPath, final String testName) {
    final LocalQuickFix fix = (LocalQuickFix)new SameParameterValueInspection().getQuickFix(actionHint.getExpectedText());
    assert fix != null;
    final int offset = getEditor().getCaretModel().getOffset();
    final PsiElement psiElement = getFile().findElementAt(offset);
    assert psiElement != null;
    final ProblemDescriptor descriptor = InspectionManager.getInstance(getProject())
      .createProblemDescriptor(psiElement, "", fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true);
    try {
      fix.applyFix(getProject(), descriptor);
      if (testName.endsWith(CONFLICT_SUFFIX)) {
        fail("Conflict expected");
      }
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (testName.endsWith(CONFLICT_SUFFIX)) {
        return;
      }
    }
    final String expectedFilePath = getBasePath() + "/after" + testName;
    checkResultByFile("In file :" + expectedFilePath, expectedFilePath, false);
  }


  @Override
  @NonNls
  protected String getBasePath() {
    return "/quickFix/SameParameterValue";
  }
}
