// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection.ConversionStrategy;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ClassCanBeRecordInspectionWeakenAccessibilityTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new ClassCanBeRecordInspection(ConversionStrategy.SHOW_AFFECTED_MEMBERS, true)};
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_LATEST;
  }

  @Override
  protected String getBasePath() {
    return "/inspection/classCanBeRecord/weakenAccessibility";
  }

  @Override
  public void runSingle() throws Throwable {
    // Run and abort (because of conflicts), and then verify that no content was changed. See IDEA-371645.
    assertThrows(BaseRefactoringProcessor.ConflictsInTestsException.class, () -> super.runSingle());
    checkResultByFile(getTestName(false) + ".java", getBasePath() + "/before" + getTestName(false), false);
    
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(super::runSingle);
  }
}
